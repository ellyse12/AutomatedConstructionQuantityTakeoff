package com.constructiontakeoff.model;

import java.sql.Timestamp;
import java.util.List;

public class TakeoffRecord {
    private int id;
    private int userId;
    private String originalFileName;
    private String processedFileName;
    private String projectName;
    private String status;
    private Timestamp takeoffTimestamp;
    private transient List<TakeoffItem> items;
    private String pdfAbsolutePath;

    public TakeoffRecord() {
    }

    public TakeoffRecord(int userId, String originalFileName, String processedFileName, String projectName,
            String status, Timestamp takeoffTimestamp) {
        this.userId = userId;
        this.originalFileName = originalFileName;
        this.processedFileName = processedFileName;
        this.projectName = projectName;
        this.status = status;
        this.takeoffTimestamp = takeoffTimestamp;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getProcessedFileName() {
        return processedFileName;
    }

    public void setProcessedFileName(String processedFileName) {
        this.processedFileName = processedFileName;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getTakeoffTimestamp() {
        return takeoffTimestamp;
    }

    public void setTakeoffTimestamp(Timestamp takeoffTimestamp) {
        this.takeoffTimestamp = takeoffTimestamp;
    }

    public List<TakeoffItem> getItems() {
        return items;
    }

    public void setItems(List<TakeoffItem> items) {
        this.items = items;
    }

    public String getPdfAbsolutePath() {
        return pdfAbsolutePath;
    }

    public void setPdfAbsolutePath(String pdfAbsolutePath) {
        this.pdfAbsolutePath = pdfAbsolutePath;
    }

    @Override
    public String toString() {
        return "TakeoffRecord{" +
                "id=" + id +
                ", userId=" + userId +
                ", originalFileName='" + originalFileName + '\'' +
                ", processedFileName='" + processedFileName + '\'' +
                ", projectName='" + projectName + '\'' +
                ", status='" + status + '\'' +
                ", takeoffTimestamp=" + takeoffTimestamp +
                ", pdfAbsolutePath='" + pdfAbsolutePath + '\'' +
                '}';
    }
}
