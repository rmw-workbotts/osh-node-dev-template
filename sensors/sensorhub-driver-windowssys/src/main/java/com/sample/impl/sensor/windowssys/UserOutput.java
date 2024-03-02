package com.sample.impl.sensor.windowssys;
import net.opengis.swe.v20.*;

import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.DataArrayImpl;
import org.vast.swe.SWEHelper;
import oshi.hardware.HWDiskStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.lang.Boolean;
import java.util.List;

public class UserOutput extends AbstractSensorOutput<SystemsInfoSensor> {
    private static final String USER_SENSOR_OUTPUT_NAME = "User Systems info";
    private static final String SENSOR_OUTPUT_LABEL = "User Systems info";
    private static final String SENSOR_OUTPUT_DESCRIPTION = "User Account Metrics returned from computer system info";
    private static final Logger logger = LoggerFactory.getLogger(SystemsInfoOutput.class);
    private static final int MAX_NUM_TIMING_SAMPLES = 10;
    private final long[] timingHistogram = new long[MAX_NUM_TIMING_SAMPLES];
    private final Object histogramLock = new Object();


    oshi.SystemInfo si = new oshi.SystemInfo();
    OperatingSystem os = si.getOperatingSystem();



    private Boolean stopProcessing = false;
    private final Object processingLock = new Object();

    private DataRecord dataStruct;
    private DataEncoding dataEncoding;
    private Thread worker1;


    UserOutput(SystemsInfoSensor parentSystemsInfoSensor) {

        super(USER_SENSOR_OUTPUT_NAME, parentSystemsInfoSensor, LoggerFactory.getLogger(SystemsInfoOutput.class));

        logger.debug("Output created");
    }




    public void doStart() {

        // Instantiate a new worker thread
        worker1 = new Thread();

        retrieveStorage();


        logger.info("Starting worker thread: {}", worker1.getName());

        // Start the worker thread
        worker1.start();

    }

    public boolean isAlive() {

        return worker1.isAlive();
    }

    @Override
    public DataComponent getRecordDescription() {

        return dataStruct;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {

        return dataEncoding;
    }

    @Override
    public double getAverageSamplingPeriod() {

        long accumulator = 0;

        synchronized (histogramLock) {

            for (int idx = 0; idx < MAX_NUM_TIMING_SAMPLES; ++idx) {

                accumulator += timingHistogram[idx];
            }
        }

        return accumulator / (double) MAX_NUM_TIMING_SAMPLES;
    }


    public void doStop() {

        synchronized (processingLock) {

            stopProcessing = true;
        }


    }

    private void initSamplingTime() {
    }


    protected void defineRecordStructure() {
        SWEHelper sweFactory = new SWEHelper();


        dataStruct = sweFactory.createRecord()
                .name(USER_SENSOR_OUTPUT_NAME)
                .updatable(true)

                .label(SENSOR_OUTPUT_LABEL)
                .description(SENSOR_OUTPUT_DESCRIPTION)
                .addField("SampleTime", sweFactory.createTime().asSamplingTimeIsoUTC().build())
                .addField("userCount", sweFactory.createCount()
                        .label("User Count")
                        .description("Number of Users on device")
                        .definition(SWEHelper.getPropertyUri("user_count"))
                        .id("userCount")
                )
                .addField("userArray", sweFactory.createArray()
                        .label("User Account Entries")
                        .description("OSHI call for Sessions- all User info on box")
                        .withVariableSize("userCount")
                        .withElement("users", sweFactory.createRecord()
                                .label("users")
                                .description("User accounts found on box")
                                .definition(SWEHelper.getPropertyUri("users"))

                                .addField("userName", sweFactory.createText()
                                        .label("User Name")
                                        .description("Name of the user account")
                                        .definition(SWEHelper.getPropertyUri("user_name"))
                                )
                                .addField("loginStatus", sweFactory.createText()
                                        .label("Login Status")
                                        .description("Null value if the user is not logged in, and the time of login if the user is.")
                                        .definition(SWEHelper.getPropertyUri("login_status"))
                                )
                                .addField("userRole", sweFactory.createText()
                                        .label("User Role")
                                        .description("What account the user is under. ex.(NT AUTHORITY, BUILTIN/Administrators, ect.")
                                        .definition(SWEHelper.getPropertyUri("user_role"))

                                )

                        ))


                .build();
        dataEncoding = sweFactory.newTextEncoding(",", "\n");
    }

    public void doInit() {

        getLogger().debug("Initializing Output");

        defineRecordStructure();


        initSamplingTime();

        getLogger().debug("Initializing Output Complete");

    }


    public void retrieveStorage() {
        System.out.println(os.getSessions());
        int setCount = 0;
        boolean processSets = true;

        long lastSetTimeMillis = System.currentTimeMillis();

        try {
            while (processSets) {

                DataBlock dataBlock;
                if (latestRecord == null) {

                    dataBlock = dataStruct.createDataBlock();

                } else {

                    dataBlock = latestRecord.renew();
                }
                synchronized (histogramLock) {

                    int setIndex = setCount % MAX_NUM_TIMING_SAMPLES;

                    // Get a sampling time for latest set based on previous set sampling time
                    timingHistogram[setIndex] = System.currentTimeMillis() - lastSetTimeMillis;

                    // Set latest sampling time to now
                    lastSetTimeMillis = timingHistogram[setIndex];
                }
                double timestamp = System.currentTimeMillis() / 1000d;
                ++setCount;


                defineRecordStructure();

                int index = 0;

                int userCount = os.getSessions().size();
                ;
                dataStruct.setData(dataBlock);

                dataBlock.setDoubleValue(index++, System.currentTimeMillis() / 1000.);
                dataBlock.setIntValue(index++, userCount);

                var userArray = ((DataArrayImpl) dataStruct.getComponent("userArray"));
                userArray.updateSize();
                dataBlock.updateAtomCount();
                int j = 0;

                for (int i = 0; i < userCount; i++) {

                    String[] currentUser = String.valueOf(os.getSessions()).split(",");


                    String userName = String.valueOf(currentUser[0 + j]);
                    String loginStatus = String.valueOf(currentUser[2 + j]);
                    String userRole = String.valueOf(currentUser[3 + j]);

                    dataBlock.setStringValue(index++, userName);
                    dataBlock.setStringValue(index++, loginStatus);
                    dataBlock.setStringValue(index++, userRole);
                    j = j + 4;


                }

                latestRecord = dataBlock;

                latestRecordTime = System.currentTimeMillis();

                eventHandler.publish(new DataEvent(latestRecordTime, UserOutput.this, dataBlock));

                synchronized (processingLock) {

                    processSets = !stopProcessing;
                }
            }


        } catch (Exception e) {

            logger.error("Error in worker thread: {}", Thread.currentThread().getName(), e);

        } finally {

            // Reset the flag so that when driver is restarted loop thread continues
            // until doStop called on the output again
            stopProcessing = false;

            logger.debug("Terminating worker thread: {}", this.name);
        }
    }
}
