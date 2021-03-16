package client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;
import commands.Command;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Controller implements Initializable {
    @FXML
    public TextArea textArea;
    @FXML
    public TextField textField;
    @FXML
    public TextField loginField;
    @FXML
    public PasswordField passwordField;
    @FXML
    public HBox authPanel;
    @FXML
    public HBox msgPanel;
    @FXML
    public ListView<String> clientList;
    
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private final int PORT = 8189;
    private final String IP_ADDRESS = "localhost";
    
    private String login;
    private String nickname;
    private Stage stage;
    private Stage regStage;
    private RegController regController;
    
    public void setAuthenticated(boolean authenticated) {
        
        msgPanel.setVisible(authenticated);
        msgPanel.setManaged(authenticated);
        authPanel.setVisible(!authenticated);
        authPanel.setManaged(!authenticated);
        clientList.setVisible(authenticated);
        clientList.setManaged(authenticated);
        textArea.clear();
        
        if (!authenticated) {
            nickname = "";
        } else {
            reader();
        }
        
        setTitle(nickname);
    }
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Platform.runLater(() -> {
            stage = (Stage)textArea.getScene().getWindow();
            stage.setOnCloseRequest(event -> {
                System.out.println("bye");
                if (socket != null && !socket.isClosed()) {
                    try {
                        out.writeUTF(Command.END);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        });
        setAuthenticated(false);
    }
    
    private void writer(String message) {
        try (FileWriter writer = new FileWriter("history_" + login + ".txt", true)) {
            
            writer.write(message);
            writer.append('\n');
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
    
    private void reader() {
        try (BufferedReader reader = new BufferedReader(new FileReader("history_" + login + ".txt"))) {
            
            String line = reader.readLine();
            int count = 0;
            while (line != null && count < 100) {
                count++;
                textArea.appendText(line + "\n");
                // считываем остальные строки в цикле
                line = reader.readLine();
            }
            
        } catch (Exception e) {
            
            if (e.getMessage().contains("Не удается найти указанный файл")) {
                System.out.println("History not found ");
            } else {
                e.printStackTrace();
            }
        }
    }
    
    private void connect() {
        try {
            socket = new Socket(IP_ADDRESS, PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            
            new Thread(() -> {
                try {
                    // цикл аутентификации
                    while (true) {
                        String str = in.readUTF();
                        
                        if (str.startsWith("/")) {
                            if (str.equals(Command.END)) {
                                throw new RuntimeException("Сервак нас отключает");
                            }
                            if (str.startsWith(Command.AUTH_OK)) {
                                String[] token = str.split("\\s");
                                nickname = token[1];
                                setAuthenticated(true);
                                break;
                            }
                            
                            if (str.equals(Command.REG_OK)) {
                                regController.setResultTryToReg(Command.REG_OK);
                            }
                            
                            if (str.equals(Command.REG_NO)) {
                                regController.setResultTryToReg(Command.REG_NO);
                            }
                        } else {
                            textArea.appendText(str + "\n");
                            writer(str);
                        }
                    }
                    // цикл работы
                    while (true) {
                        String str = in.readUTF();
                        
                        if (str.startsWith("/")) {
                            if (str.equals(Command.END)) {
                                System.out.println("Client disconnected");
                                break;
                            }
                            if (str.startsWith(Command.CLIENT_LIST)) {
                                String[] token = str.split("\\s");
                                Platform.runLater(() -> {
                                    clientList.getItems().clear();
                                    for (int i = 1; i < token.length; i++) {
                                        clientList.getItems().add(token[i]);
                                    }
                                });
                            }
                            
                        } else {
                            textArea.appendText(str + "\n");
                            writer(str);
                        }
                    }
                } catch (RuntimeException e) {
                    System.out.println(e.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    setAuthenticated(false);
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void sendMsg(ActionEvent actionEvent) {
        try {
            out.writeUTF(textField.getText());
            textField.clear();
            textField.requestFocus();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void tryToAuth(ActionEvent actionEvent) {
        if (socket == null || socket.isClosed()) {
            connect();
        }
        
        login = loginField.getText().trim();
        
        try {
            out.writeUTF(
                String.format("%s %s %s", Command.AUTH, loginField.getText().trim(), passwordField.getText().trim()));
            
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            passwordField.clear();
        }
    }
    
    private void setTitle(String nickname) {
        Platform.runLater(() -> {
            if (nickname.equals("")) {
                stage.setTitle("Best chat of World");
            } else {
                stage.setTitle(String.format("Best chat of World - [ %s ]", nickname));
            }
        });
    }
    
    public void clientListMouseReleased(MouseEvent mouseEvent) {
        System.out.println(clientList.getSelectionModel().getSelectedItem());
        String msg = String.format("%s %s ", Command.PRIVATE_MSG, clientList.getSelectionModel().getSelectedItem());
        textField.setText(msg);
    }
    
    public void showRegWindow(ActionEvent actionEvent) {
        if (regStage == null) {
            initRegWindow();
        }
        regStage.show();
    }
    
    private void initRegWindow() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/reg.fxml"));
            Parent root = fxmlLoader.load();
            
            regController = fxmlLoader.getController();
            regController.setController(this);
            
            regStage = new Stage();
            regStage.setTitle("Best chat of World registration");
            regStage.setScene(new Scene(root, 450, 350));
            regStage.initStyle(StageStyle.UTILITY);
            regStage.initModality(Modality.APPLICATION_MODAL);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void registration(String login, String password, String nickname) {
        if (socket == null || socket.isClosed()) {
            connect();
        }
        try {
            out.writeUTF(String.format("%s %s %s %s", Command.REG, login, password, nickname));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
