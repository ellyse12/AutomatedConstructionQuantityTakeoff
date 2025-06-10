package com.constructiontakeoff.controller;

import com.constructiontakeoff.model.User;
import com.constructiontakeoff.util.DatabaseService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Optional;

public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label messageLabel;

    private final DatabaseService dbService = DatabaseService.getInstance();

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Please fill in all fields");
            return;
        }

        Optional<User> user = dbService.authenticateUser(username, password);
        if (user.isPresent()) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/constructiontakeoff/view/home.fxml"));
                Parent root = loader.load();

                HomeController homeController = loader.getController();
                homeController.initData(user.get());

                Stage stage = (Stage) usernameField.getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.setTitle("Construction Takeoff - Home");
                stage.show();
            } catch (IOException e) {
                messageLabel.setText("Error loading home screen");
                e.printStackTrace();
            }
        } else {
            messageLabel.setText("Invalid username or password");
        }
    }

    @FXML
    private void handleSignupNavigation() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/constructiontakeoff/view/signup.fxml"));
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Construction Takeoff - Sign Up");
            stage.show();
        } catch (IOException e) {
            messageLabel.setText("Error loading signup screen");
            e.printStackTrace();
        }
    }
}
