package com.constructiontakeoff.controller;

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

public class SignupController {
    @FXML
    private TextField usernameField;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label messageLabel;

    private final DatabaseService dbService = DatabaseService.getInstance();

    @FXML
    private void handleSignup() {
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            messageLabel.setText("Please fill in all fields");
            return;
        }

        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            messageLabel.setText("Please enter a valid email address");
            return;
        }

        if (!password.equals(confirmPassword)) {
            messageLabel.setText("Passwords do not match");
            return;
        }

        if (password.length() < 6) {
            messageLabel.setText("Password must be at least 6 characters long");
            return;
        }

        if (dbService.isUsernameTaken(username)) {
            messageLabel.setText("Username is already taken");
            return;
        }

        if (dbService.isEmailTaken(email)) {
            messageLabel.setText("Email is already registered");
            return;
        }

        if (dbService.registerUser(username, email, password)) {
            try {
                Parent root = FXMLLoader.load(getClass().getResource("/com/constructiontakeoff/view/login.fxml"));
                Stage stage = (Stage) usernameField.getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.setTitle("Construction Takeoff - Login");
                stage.show();
            } catch (IOException e) {
                messageLabel.setText("Error navigating to login screen");
                e.printStackTrace();
            }
        } else {
            messageLabel.setText("Error registering user. Please try again.");
        }
    }

    @FXML
    private void handleBackToLogin() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/constructiontakeoff/view/login.fxml"));
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Construction Takeoff - Login");
            stage.show();
        } catch (IOException e) {
            messageLabel.setText("Error navigating to login screen");
            e.printStackTrace();
        }
    }
}
