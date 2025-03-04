//  Copyright 2020 VMware, Inc.
//  SPDX-License-Identifier: Apache-2.0
//

package com.idcta.proj.sensor.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.ScanRecord;

import com.idcta.proj.sensor.datatype.Calibration;
import com.idcta.proj.sensor.datatype.CalibrationMeasurementUnit;
import com.idcta.proj.sensor.datatype.Data;
import com.idcta.proj.sensor.datatype.PayloadData;
import com.idcta.proj.sensor.datatype.PseudoDeviceAddress;
import com.idcta.proj.sensor.datatype.RSSI;
import com.idcta.proj.sensor.datatype.TargetIdentifier;
import com.idcta.proj.sensor.datatype.TimeInterval;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;

public class BLEDevice {
    /// Device registration timestamp
    public final Date createdAt;
    /// Last time anything changed, e.g. attribute update
    public Date lastUpdatedAt = null;
    /// Ephemeral device identifier, e.g. peripheral identifier UUID
    public final TargetIdentifier identifier;
    /// Pseudo device address for tracking Android devices that change address constantly.
    private PseudoDeviceAddress pseudoDeviceAddress = null;
    /// Delegate for listening to attribute updates events.
    private final BLEDeviceDelegate delegate;
    /// Android Bluetooth device object for interacting with this device.
    private BluetoothDevice peripheral = null;
    /// Bluetooth device connection state.
    private BLEDeviceState state = BLEDeviceState.disconnected;
    /// Device operating system, this is necessary for selecting different interaction procedures for each platform.
    private BLEDeviceOperatingSystem operatingSystem = BLEDeviceOperatingSystem.unknown;
    /// Payload data acquired from the device via payloadCharacteristic read, e.g. C19X beacon code or Sonar encrypted identifier
    private PayloadData payloadData = null;
    private Date lastPayloadDataUpdate = null;
    /// Immediate Send data to send next
    private Data immediateSendData = null;
    /// Most recent RSSI measurement taken by readRSSI or didDiscover.
    private RSSI rssi = null;
    /// Transmit power data where available (only provided by Android devices)
    private BLE_TxPower txPower = null;
    /// Is device receive only?
    private boolean receiveOnly = false;
    /// Ignore logic
    private TimeInterval ignoreForDuration = null;
    private Date ignoreUntil = null;
    private ScanRecord scanRecord = null;

    /// BLE characteristics
    private BluetoothGattCharacteristic signalCharacteristic = null;
    private BluetoothGattCharacteristic payloadCharacteristic = null;
    private BluetoothGattCharacteristic legacyPayloadCharacteristic = null;
    protected byte[] signalCharacteristicWriteValue = null;
    protected Queue<byte[]> signalCharacteristicWriteQueue = null;

    private BluetoothGattCharacteristic modelCharacteristic = null;
    private String model = null;
    private BluetoothGattCharacteristic deviceNameCharacteristic = null;
    private String deviceName = null;

    /// Track connection timestamps
    private Date lastDiscoveredAt = null;
    private Date lastConnectedAt = null;

    /// Payload data already shared with this peer
    protected final List<PayloadData> payloadSharingData = new ArrayList<>();

    /// Track write timestamps
    private Date lastWritePayloadAt = null;
    private Date lastWriteRssiAt = null;
    private Date lastWritePayloadSharingAt = null;

    public TimeInterval timeIntervalSinceConnected() {
        if (state() != BLEDeviceState.connected) {
            return TimeInterval.zero;
        }
        if (lastConnectedAt == null) {
            return TimeInterval.zero;
        }
        return new TimeInterval((new Date().getTime() - lastConnectedAt.getTime()) / 1000);
    }

    /// Time interval since last attribute value update, this is used to identify devices that may have expired and should be removed from the database.
    /// This is also used by immediateSendAll to choose targets
    public TimeInterval timeIntervalSinceLastUpdate() {
        if (lastUpdatedAt == null) {
            return TimeInterval.never;
        }
        return new TimeInterval((new Date().getTime() - lastUpdatedAt.getTime()) / 1000);
    }

    public String description() {
        return "BLEDevice[" +
                "id=" + identifier +
                ",os=" + operatingSystem +
                ",payload=" + payloadData() +
                (pseudoDeviceAddress() != null ? ",address=" + pseudoDeviceAddress() : "") +
                (deviceName() != null ? ",name=" + deviceName() : "") +
                (model() != null ? ",model=" + model() : "") +
                "]";
    }

    public BLEDevice(TargetIdentifier identifier, BLEDeviceDelegate delegate) {
        this.createdAt = new Date();
        this.identifier = identifier;
        this.delegate = delegate;
        this.lastUpdatedAt = createdAt;
    }

    /// Create a clone of an existing device
    public BLEDevice(BLEDevice device, BluetoothDevice bluetoothDevice) {
        this.createdAt = device.createdAt;
        this.lastUpdatedAt = new Date();
        this.identifier = new TargetIdentifier(bluetoothDevice);
        this.pseudoDeviceAddress = device.pseudoDeviceAddress;
        this.delegate = device.delegate;
        this.state = device.state;
        this.operatingSystem = device.operatingSystem;
        this.payloadData = device.payloadData;
        this.lastPayloadDataUpdate = device.lastPayloadDataUpdate;
        this.rssi = device.rssi;
        this.txPower = device.txPower;
        this.receiveOnly = device.receiveOnly;
        this.ignoreForDuration = device.ignoreForDuration;
        this.ignoreUntil = device.ignoreUntil;
        this.scanRecord = device.scanRecord;
        this.signalCharacteristic = device.signalCharacteristic;
        this.payloadCharacteristic = device.payloadCharacteristic;
        this.modelCharacteristic = device.modelCharacteristic;
        this.deviceNameCharacteristic = device.deviceNameCharacteristic;
        this.model = device.model;
        this.deviceName = device.deviceName;
        this.signalCharacteristicWriteValue = device.signalCharacteristicWriteValue;
        this.signalCharacteristicWriteQueue = device.signalCharacteristicWriteQueue;
        this.legacyPayloadCharacteristic = device.legacyPayloadCharacteristic;
        this.lastDiscoveredAt = device.lastDiscoveredAt;
        this.lastConnectedAt = device.lastConnectedAt;
        this.payloadSharingData.addAll(device.payloadSharingData);
        this.lastWritePayloadAt = device.lastWritePayloadAt;
        this.lastWriteRssiAt = device.lastWriteRssiAt;
        this.lastWritePayloadSharingAt = device.lastWritePayloadSharingAt;
    }

    public PseudoDeviceAddress pseudoDeviceAddress() {
        return pseudoDeviceAddress;
    }

    public void pseudoDeviceAddress(PseudoDeviceAddress pseudoDeviceAddress) {
        if (this.pseudoDeviceAddress == null || !this.pseudoDeviceAddress.equals(pseudoDeviceAddress)) {
            this.pseudoDeviceAddress = pseudoDeviceAddress;
            lastUpdatedAt = new Date();
        }
    }

    public BluetoothDevice peripheral() {
        return peripheral;
    }

    public void peripheral(BluetoothDevice peripheral) {
        if (this.peripheral != peripheral) {
            this.peripheral = peripheral;
            lastUpdatedAt = new Date();
        }
    }

    public BLEDeviceState state() {
        return state;
    }

    public void state(BLEDeviceState state) {
        this.state = state;
        lastUpdatedAt = new Date();
        if (state == BLEDeviceState.connected) {
            lastConnectedAt = lastUpdatedAt;
        }
        delegate.device(this, BLEDeviceAttribute.state);
    }

    public BLEDeviceOperatingSystem operatingSystem() {
        return operatingSystem;
    }

    public void operatingSystem(BLEDeviceOperatingSystem operatingSystem) {
        lastUpdatedAt = new Date();
        // Set ignore timer
        if (operatingSystem == BLEDeviceOperatingSystem.ignore) {
            if (ignoreForDuration == null) {
                ignoreForDuration = TimeInterval.minute;
            } else if (ignoreForDuration.value < TimeInterval.minutes(3).value) {
                ignoreForDuration = new TimeInterval(Math.round(ignoreForDuration.value * 1.2));
            }
            ignoreUntil = new Date(lastUpdatedAt.getTime() + ignoreForDuration.millis());
        } else {
            ignoreUntil = null;
        }
        // Reset ignore for duration and request count if operating system has been confirmed
        if (operatingSystem == BLEDeviceOperatingSystem.ios || operatingSystem == BLEDeviceOperatingSystem.android) {
            ignoreForDuration = null;
        }
        // Set operating system
        if (this.operatingSystem != operatingSystem) {
            this.operatingSystem = operatingSystem;
            delegate.device(this, BLEDeviceAttribute.operatingSystem);
        }
    }

    /// Should ignore this device for now.
    public boolean ignore() {
        if (ignoreUntil == null) {
            return false;
        }
        if (new Date().getTime() < ignoreUntil.getTime()) {
            return true;
        }
        return false;
    }

    public PayloadData payloadData() {
        return payloadData;
    }

    public void payloadData(PayloadData payloadData) {
        this.payloadData = payloadData;
        lastPayloadDataUpdate = new Date();
        lastUpdatedAt = lastPayloadDataUpdate;
        delegate.device(this, BLEDeviceAttribute.payloadData);
    }

    public TimeInterval timeIntervalSinceLastPayloadDataUpdate() {
        if (lastPayloadDataUpdate == null) {
            return TimeInterval.never;
        }
        return new TimeInterval((new Date().getTime() - lastPayloadDataUpdate.getTime()) / 1000);
    }

    public void immediateSendData(Data immediateSendData) {
        this.immediateSendData = immediateSendData;
    }

    public Data immediateSendData() {
        return immediateSendData;
    }

    public RSSI rssi() {
        return rssi;
    }

    public void rssi(RSSI rssi) {
        this.rssi = rssi;
        lastUpdatedAt = new Date();
        delegate.device(this, BLEDeviceAttribute.rssi);
    }

    public void legacyPayloadCharacteristic(BluetoothGattCharacteristic characteristic) {
        this.legacyPayloadCharacteristic = characteristic;
        lastPayloadDataUpdate = new Date();
    }

    public BluetoothGattCharacteristic getLegacyPayloadCharacteristic() {
        return  legacyPayloadCharacteristic;
    }

    public BLE_TxPower txPower() {
        return txPower;
    }

    public void txPower(BLE_TxPower txPower) {
        this.txPower = txPower;
        lastUpdatedAt = new Date();
        delegate.device(this, BLEDeviceAttribute.txPower);
    }

    public Calibration calibration() {
        if (txPower == null) {
            return null;
        }
        return new Calibration(CalibrationMeasurementUnit.BLETransmitPower, new Double(txPower.value));
    }

    public boolean receiveOnly() {
        return receiveOnly;
    }

    public void receiveOnly(boolean receiveOnly) {
        this.receiveOnly = receiveOnly;
        lastUpdatedAt = new Date();
    }

    public void invalidateCharacteristics() {
        signalCharacteristic = null;
        payloadCharacteristic = null;
        modelCharacteristic = null;
        deviceNameCharacteristic = null;
        legacyPayloadCharacteristic = null;
    }

    public BluetoothGattCharacteristic signalCharacteristic() {
        return signalCharacteristic;
    }

    public void signalCharacteristic(BluetoothGattCharacteristic characteristic) {
        this.signalCharacteristic = characteristic;
        lastUpdatedAt = new Date();
    }

    public BluetoothGattCharacteristic payloadCharacteristic() {
        return payloadCharacteristic;
    }

    public void payloadCharacteristic(BluetoothGattCharacteristic characteristic) {
        this.payloadCharacteristic = characteristic;
        lastUpdatedAt = new Date();
    }

    public boolean supportsModelCharacteristic() { return null != modelCharacteristic; }

    public BluetoothGattCharacteristic modelCharacteristic() { return modelCharacteristic; }

    public void modelCharacteristic(BluetoothGattCharacteristic modelCharacteristic) {
        this.modelCharacteristic = modelCharacteristic;
        lastUpdatedAt = new Date();
    }

    public boolean supportsDeviceNameCharacteristic() { return null != deviceNameCharacteristic; }

    public BluetoothGattCharacteristic deviceNameCharacteristic() { return deviceNameCharacteristic; }

    public void deviceNameCharacteristic(BluetoothGattCharacteristic deviceNameCharacteristic) {
        this.deviceNameCharacteristic = deviceNameCharacteristic;
        lastUpdatedAt = new Date();
    }

    public String deviceName() { return deviceName; }

    public void deviceName(String deviceName) {
        this.deviceName = deviceName;
        lastUpdatedAt = new Date();
    }

    public String model() { return model; }

    public void model(String model) {
        this.model = model;
        lastUpdatedAt = new Date();
    }

    public void registerDiscovery() {
        lastDiscoveredAt = new Date();
        lastUpdatedAt = lastDiscoveredAt;
    }

    public void registerWritePayload() {
        lastUpdatedAt = new Date();
        lastWritePayloadAt = lastUpdatedAt;
    }

    public TimeInterval timeIntervalSinceLastWritePayload() {
        if (lastWritePayloadAt == null) {
            return TimeInterval.never;
        }
        return new TimeInterval((new Date().getTime() - lastWritePayloadAt.getTime()) / 1000);
    }

    public void registerWriteRssi() {
        lastUpdatedAt = new Date();
        lastWriteRssiAt = lastUpdatedAt;
    }

    public TimeInterval timeIntervalSinceLastWriteRssi() {
        if (lastWriteRssiAt == null) {
            return TimeInterval.never;
        }
        return new TimeInterval((new Date().getTime() - lastWriteRssiAt.getTime()) / 1000);
    }

    public void registerWritePayloadSharing() {
        lastUpdatedAt = new Date();
        lastWritePayloadSharingAt = lastUpdatedAt;
    }

    public TimeInterval timeIntervalSinceLastWritePayloadSharing() {
        if (lastWritePayloadSharingAt == null) {
            return TimeInterval.never;
        }
        return new TimeInterval((new Date().getTime() - lastWritePayloadSharingAt.getTime()) / 1000);
    }

    public TimeInterval timeIntervalUntilIgnoreExpires() {
        if (ignoreUntil == null) {
            return TimeInterval.zero;
        }
        if (ignoreUntil.getTime() == Long.MAX_VALUE) {
            return TimeInterval.never;
        }
        return new TimeInterval((ignoreUntil.getTime() - new Date().getTime()) / 1000);
    }

    public void scanRecord(ScanRecord scanRecord) {
        this.scanRecord = scanRecord;
    }

    public ScanRecord scanRecord() {
        return scanRecord;
    }

    @Override
    public String toString() {
        return description();
    }
}
