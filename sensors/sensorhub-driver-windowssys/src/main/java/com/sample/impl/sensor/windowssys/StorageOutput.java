package com.sample.impl.sensor.windowssys;

import net.opengis.swe.v20.*;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.DataArrayImpl;
import org.vast.swe.SWEHelper;
import oshi.hardware.HWDiskStore;

import java.lang.Boolean;
import java.util.List;

public class StorageOutput extends AbstractSensorOutput<SystemsInfoSensor> implements Runnable{
    private static final String DISK_SENSOR_OUTPUT_NAME = "Storage Systems info";
    private static final String SENSOR_OUTPUT_LABEL = "Storage Systems info";
    private static final String SENSOR_OUTPUT_DESCRIPTION = "Disk Storage Metrics returned from computer system info";
    private static final Logger logger = LoggerFactory.getLogger(SystemsInfoOutput.class);
    private static final int MAX_NUM_TIMING_SAMPLES = 10;
    private final long[] timingHistogram = new long[MAX_NUM_TIMING_SAMPLES];
    private final Object histogramLock = new Object();


    oshi.SystemInfo si = new oshi.SystemInfo();


    private Boolean stopProcessing = false;
    private final Object processingLock = new Object();
    private DataArray diskStores;
    private DataRecord dataStruct;
    private DataEncoding dataEncoding;
    private Thread worker1;


    StorageOutput(SystemsInfoSensor parentSystemsInfoSensor) {

        super(DISK_SENSOR_OUTPUT_NAME, parentSystemsInfoSensor, LoggerFactory.getLogger(SystemsInfoOutput.class));

        logger.debug("Output created");
    }


    public void doStart() {

        // Instantiate a new worker thread
//        Runnable task =  this::run;
        worker1 = new Thread(this,this.name);


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
                .name(DISK_SENSOR_OUTPUT_NAME)
                .updatable(true)

                .label(SENSOR_OUTPUT_LABEL)
                .description(SENSOR_OUTPUT_DESCRIPTION)
                .addField("SampleTime", sweFactory.createTime().asSamplingTimeIsoUTC().build())
                .addField("diskStorageCount", sweFactory.createCount()
                        .label("Disk Storage Count")
                        .description("Number of returned disk drives on device")
                        .definition(SWEHelper.getPropertyUri("disk_storage_count"))
                        .id("diskStorageCount")
                )
                .addField("diskStorageArray", sweFactory.createArray()
                        .label("Disk Storage Entries")
                        .description("OSHI call for DiskStores- all storage devices on box that executes build")
                        .withVariableSize("diskStorageCount")
                        .withElement("diskStorage", sweFactory.createRecord()
                                .label("Disk Storage")
                                .description("Disk Storage Device found on box")
                                .definition(SWEHelper.getPropertyUri("disk_storage"))

                                .addField("storageDeviceNumber", sweFactory.createText()
                                        .label("Device Number")
                                        .description("Number assigned to disk device.")
                                        .definition(SWEHelper.getPropertyUri("storage_dev_number"))
                                )
                                .addField("modelName", sweFactory.createText()
                                        .label("Device Model")
                                        .description("Model of Disk Device.")
                                        .definition(SWEHelper.getPropertyUri("device_model"))
                                )
                                .addField("diskSize", sweFactory.createQuantity()
                                        .label("Device Size")
                                        .uomCode("B")
                                        .description("Size of Disk Device.")
                                        .definition(SWEHelper.getPropertyUri("device_size"))

                                )
                                .addField("diskRead", sweFactory.createQuantity()
                                        .label("Device Read Size")
                                        .description("Size of Disk Read GIB")
                                        .uomCode("GB")
                                        .definition(SWEHelper.getPropertyUri("device_read_size"))
                                )
                                .addField("diskWrite", sweFactory.createQuantity()
                                        .label("Device Write Size")
                                        .uomCode("GB")
                                        .description("Size of Disk Write GIB")
                                        .definition(SWEHelper.getPropertyUri("device_write_size"))
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


    public void run() {
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

                ++setCount;
                double timestamp = System.currentTimeMillis() / 1000d;

                List<HWDiskStore> diskList = si.getHardware().getDiskStores();


                defineRecordStructure();

                int index = 0;

                int storageCount = si.getHardware().getDiskStores().size();
                ;
                dataStruct.setData(dataBlock);

                dataBlock.setDoubleValue(index++, timestamp);
                dataBlock.setIntValue(index++, storageCount);

                var diskStorageArray = ((DataArrayImpl) dataStruct.getComponent("diskStorageArray"));
                diskStorageArray.updateSize();
                dataBlock.updateAtomCount();


                for (HWDiskStore disk : diskList) {


                    String modelName = disk.getModel();
                    String diskName = disk.getName();
                    long diskSize = disk.getSize();
                    long readSize = disk.getReads();
                    long writeSize = disk.getWrites();

                    dataBlock.setStringValue(index++, modelName);
                    dataBlock.setStringValue(index++, diskName);
                    dataBlock.setLongValue(index++, diskSize);
                    dataBlock.setLongValue(index++, readSize);
                    dataBlock.setLongValue(index++, writeSize);
                }

                latestRecord = dataBlock;

                latestRecordTime = System.currentTimeMillis();

                eventHandler.publish(new DataEvent(latestRecordTime, StorageOutput.this, dataBlock));

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