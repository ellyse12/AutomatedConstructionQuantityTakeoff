package com.constructiontakeoff.model;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;

public class QuantityItem {
    private final SimpleStringProperty material;
    private final SimpleDoubleProperty quantity;
    private final SimpleStringProperty unit;

    public QuantityItem(String material, double quantity, String unit) {
        this.material = new SimpleStringProperty(material);
        this.quantity = new SimpleDoubleProperty(quantity);
        this.unit = new SimpleStringProperty(unit);
    }

    public String getMaterial() {
        return material.get();
    }

    public SimpleStringProperty materialProperty() {
        return material;
    }

    public double getQuantity() {
        return quantity.get();
    }

    public SimpleDoubleProperty quantityProperty() {
        return quantity;
    }

    public String getUnit() {
        return unit.get();
    }

    public SimpleStringProperty unitProperty() {
        return unit;
    }
}
