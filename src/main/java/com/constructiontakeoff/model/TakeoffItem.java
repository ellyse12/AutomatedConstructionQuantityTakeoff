package com.constructiontakeoff.model;

public class TakeoffItem {
    private int id;
    private int takeoffRecordId;
    private String material;
    private double quantity;
    private String unit;

    public TakeoffItem() {
    }

    public TakeoffItem(int takeoffRecordId, String material, double quantity, String unit) {
        this.takeoffRecordId = takeoffRecordId;
        this.material = material;
        this.quantity = quantity;
        this.unit = unit;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTakeoffRecordId() {
        return takeoffRecordId;
    }

    public void setTakeoffRecordId(int takeoffRecordId) {
        this.takeoffRecordId = takeoffRecordId;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    @Override
    public String toString() {
        return "TakeoffItem{" +
                "id=" + id +
                ", takeoffRecordId=" + takeoffRecordId +
                ", material='" + material + '\'' +
                ", quantity=" + quantity +
                ", unit='" + unit + '\'' +
                '}';
    }
}
