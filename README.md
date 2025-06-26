# Construction Takeoff System

This project is a JavaFX desktop application for generating quantity takeoffs from building plans. Users can import DWG or PDF drawings, run an automated engine to calculate material quantities and export the results to Excel. A history view lets users review past takeoffs.

## Features
- Login and signup screens for user authentication
- Import DWG files (converted to PDF for viewing)
- Zoomable PDF viewer and results table
- Automated takeoff engine that parses lines, polylines and other entities
- Export takeoff results to Excel
- History view with PDF preview and Excel download
- Data stored locally using H2/SQLite

## Building
Prerequisites:
- Java 17+
- Maven 3

To build the project run:
```bash
mvn package
```
This creates a shaded JAR under `target/`.

## Running
During development you can run the UI with:
```bash
mvn javafx:run
```
Or execute the packaged jar:
```bash
java -jar target/construction-takeoff-1.0-SNAPSHOT.jar
```
After logging in, open a drawing and click **Start Takeoff** to generate quantities. Results can be exported and viewed later in the history tab.

## Repository Structure
- `src/main/java` – application source code
- `src/main/resources` – FXML views and styles
- `pdf` – sample project files
- `pom.xml` – Maven build configuration
