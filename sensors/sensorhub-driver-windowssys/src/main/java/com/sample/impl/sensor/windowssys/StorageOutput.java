package com.sample.impl.sensor.windowssys;

import net.opengis.swe.v20.*;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.SWEFactory;
import org.vast.swe.SWEHelper;
import oshi.hardware.HardwareAbstractionLayer;
import org.vast.data.DataArrayImpl;
import java.awt.*;
import java.lang.Boolean;
import java.util.ArrayList;

public class StorageOutput  extends AbstractSensorOutput<Sensor>{
    private static final String DISK_SENSOR_OUTPUT_NAME = "Storage Systems info";
    private static final String SENSOR_OUTPUT_LABEL = "Storage Systems info";
    private static final String SENSOR_OUTPUT_DESCRIPTION = "Disk Storage Metrics returned from computer system info";
    private static final Logger logger = LoggerFactory.getLogger(Output.class);
    private ArrayList<Object> DiskCount;
    private ArrayList<Object> ModelName;

    private ArrayList<Integer> DiskSize;

    private ArrayList<Integer> DiskWrite;

    private ArrayList<Object> DiskRead;
    oshi.SystemInfo si = new oshi.SystemInfo();
    HardwareAbstractionLayer hal = si.getHardware();
    String h2 = String.valueOf(hal.getDiskStores());
    ArrayList<String> oshiH = new ArrayList<String>();
    private Boolean stopProcessing = false;
    private final Object processingLock = new Object();
    private DataArray diskStores;
    private DataRecord dataStruct;
    private DataEncoding dataEncoding;
    private Thread worker1;


    /**
     * Constructor
     *
     * @param parentSensor Sensor driver providing this output
     */
    StorageOutput(Sensor parentSensor) {

        super(DISK_SENSOR_OUTPUT_NAME, parentSensor);

        logger.debug("Output created");
    }

    public void doInit() {

        getLogger().debug("Initializing Output");

        defineRecordStructure();



        initSamplingTime();

        getLogger().debug("Initializing Output Complete");

    }


    public void doStart() {

        // Instantiate a new worker thread
        worker1 = new Thread((Runnable) this, this.name);

        // TODO: Perform other startup

        logger.info("Starting worker thread: {}", worker1.getName());

        // Start the worker thread
        worker1.start();
    }
    public void doStop() {

        synchronized (processingLock) {

            stopProcessing = true;
        }


    }
    private void initSamplingTime() {
    }

//
//        StringBuilder finalDiskString = new StringBuilder();
//        for (String item : oshiH)
//        {
//            finalDiskString.append(item).append("\n");
//        }return finalDiskString;
//    }


    protected void defineRecordStructure() {
        SWEHelper sweFactory = new SWEHelper();


        dataStruct = sweFactory.createRecord()
                .name(DISK_SENSOR_OUTPUT_NAME)
                .updatable(true)
                .definition(SWEHelper.getPropertyUri(DISK_SENSOR_OUTPUT_NAME))
                .label(SENSOR_OUTPUT_LABEL)
                .description(SENSOR_OUTPUT_DESCRIPTION)
                .addField("SampleTime", sweFactory.createTime().asSamplingTimeIsoUTC().build())
                .addField("diskStorageCount", sweFactory.createCount()
                        .label("Disk Storage Count")
                        .description("Number of returned disk drives on device")
                        .definition(SWEHelper.getPropertyUri("disk_storage_count"))
                )
                .addField("diskStorageArray", sweFactory.createArray()
                        .label("Disk Storage Entries")
                        .description("OSHI call for DiskStores- all storage devices on box that executes build")
                        .withVariableSize("diskStorageArray")
                        .withElement("diskStorage", sweFactory.createRecord()
                                .label("Disk Storage")
                                .description("Disk Storage Device found on box")
                                .definition(SWEHelper.getPropertyUri("disk_storage_device"))
                                .addField("storageDeviceNumber", sweFactory.createText()
                                        .label("Device Number")
                                        .description("Number assigned to disk device.")
                                        .definition(SWEHelper.getPropertyUri(""))
                                )
                                .addField("modelName",sweFactory.createText()
                                        .label("Device Model")
                                        .description("Model of Disk Device.")
                                        .definition(SWEHelper.getPropertyUri(""))
                                )
                                .addField("diskSize",sweFactory.createQuantity()
                                        .label("Device Size")
                                        .description("Size of Disk Device.")
                                        .definition(SWEHelper.getPropertyUri(""))
                                )
                                .addField("diskRead",sweFactory.createQuantity()
                                        .label("Device Read Size")
                                        .description("Size of Disk Read GIB")
                                        .definition(SWEHelper.getPropertyUri(""))
                                )
                                .addField("diskWrite", sweFactory.createText()
                                        .label("Device Write Size")
                                        .description("Size of Disk Write GIB")
                                        .definition(SWEHelper.getPropertyUri(""))
                                )
                        ))












                .build();
        dataEncoding = sweFactory.newTextEncoding(",", "\n");
    }
    private ArrayList printStorageSpace() {
        String[] parts = h2.split(":");

        oshiH.clear();

        for (String part : parts) {
            oshiH.add(part);
        }
            return oshiH;}
    private ArrayList getDiskNum(){
        for (int i=0; i < printStorageSpace().size();) {
            DiskCount.add(printStorageSpace().get(i));
            i = i +7;
        }
        return DiskCount;
    }
    private ArrayList getModelName(){
        for (int i=1; i < printStorageSpace().size();) {
            DiskCount.add(printStorageSpace().get(i));
            i = i +7;
        }
        return DiskCount;
    }

    private ArrayList getDiskSize(){
        for (int i=3; i < printStorageSpace().size();) {
            DiskCount.add(printStorageSpace().get(i));
            i = i +7;
        }
        return DiskCount;
    }

    private ArrayList getDiskRead(){
        for (int i=4; i < printStorageSpace().size();) {
            DiskCount.add(printStorageSpace().get(i));
            i = i +7;
        }
        return DiskCount;
    }

    private ArrayList getDiskWrite(){
        for (int i=5; i < printStorageSpace().size();) {
            DiskCount.add(printStorageSpace().get(i));
            i = i +7;
        }
        return DiskCount;
    }


    public void onNewMessage(Object object) {
        defineRecordStructure();
        DataBlock dataBlock = dataStruct.createDataBlock();
        dataStruct.setData(dataBlock);


        ArrayList diskStoresArray = printStorageSpace();
        int index = 0;
        dataBlock.setDoubleValue(index++, System.currentTimeMillis() / 1000.);

        dataBlock.setIntValue(index++, diskStoresArray.size());

        var diskStorageArray = ((DataArrayImpl) dataStruct.getComponent("diskStorageArray"));
        diskStorageArray.updateSize();
        dataBlock.updateAtomCount();

        for (int i=0; i < diskStoresArray.size(); i++) {


            dataBlock.setStringValue(index++, String.valueOf(getDiskNum().get(i)));
            dataBlock.setStringValue(index++, String.valueOf(getModelName().get(i)));
            dataBlock.setStringValue(index++, String.valueOf(getDiskSize().get(i)));
            dataBlock.setStringValue(index++, String.valueOf(getDiskRead().get(i)));
            dataBlock.setStringValue(index++, String.valueOf(getDiskWrite().get(i)));



        }
        latestRecord = dataBlock;

        latestRecordTime = System.currentTimeMillis();

        eventHandler.publish(new DataEvent(latestRecordTime, StorageOutput.this, dataBlock));


    }
    @Override
    public DataComponent getRecordDescription() {
        return null;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {
        return null;
    }

    @Override
    public double getAverageSamplingPeriod() {
        return 0;
    }
}
