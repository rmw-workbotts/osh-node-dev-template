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

public class StorageOutput extends AbstractSensorOutput<Sensor> {
    private static final String DISK_SENSOR_OUTPUT_NAME = "Storage Systems info";
    private static final String SENSOR_OUTPUT_LABEL = "Storage Systems info";
    private static final String SENSOR_OUTPUT_DESCRIPTION = "Disk Storage Metrics returned from computer system info";
    private static final Logger logger = LoggerFactory.getLogger(Output.class);
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


    StorageOutput(Sensor parentSensor) {

        super(DISK_SENSOR_OUTPUT_NAME, parentSensor, LoggerFactory.getLogger(Output.class));

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

        return true;
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
                                        .description("Size of Disk Device.")
                                        .definition(SWEHelper.getPropertyUri("device_size"))
                                )
                                .addField("diskRead", sweFactory.createQuantity()
                                        .label("Device Read Size")
                                        .description("Size of Disk Read GIB")
                                        .definition(SWEHelper.getPropertyUri("device_read_size"))
                                )
                                .addField("diskWrite", sweFactory.createQuantity()
                                        .label("Device Write Size")
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


    public void retrieveStorage() {


        List<HWDiskStore> diskList = si.getHardware().getDiskStores();


        defineRecordStructure();
        DataBlock dataBlock = dataStruct.createDataBlock();
        int index = 0;

        int storageCount = si.getHardware().getDiskStores().size();
        ;
        dataStruct.setData(dataBlock);

        dataBlock.setDoubleValue(index++, System.currentTimeMillis() / 1000.);
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
    }
}