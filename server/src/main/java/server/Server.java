package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import commands.Command;

public class Server {
    private final int PORT = 8189;
    private ServerSocket server;
    private Socket socket;
    private Connection connection;
    private Statement statement;
    
    private List<ClientHandler> clients;
    private AuthService authService;
    
    public Server() {
        
        try {
            createConnection();
            System.out.println("connect ok");
            
        } catch (Exception e1) {
            e1.printStackTrace();
            disconnect();
        }
        
        clients = new CopyOnWriteArrayList<>();
        authService = new SimpleAuthServise(statement);
        
        try {
            server = new ServerSocket(PORT);
            System.out.println("Server started");
            
            while (true) {
                socket = server.accept();
                System.out.println("Client connected");
                System.out.println("client: " + socket.getRemoteSocketAddress());
                new ClientHandler(this, socket);
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            disconnect();
            
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    public void broadcastMsg(ClientHandler sender, String msg) {
        String message = String.format("[ %s ]: %s", sender.getNickname(), msg);
        for (ClientHandler c : clients) {
            c.sendMsg(message);
        }
    }
    
    public void privateMsg(ClientHandler sender, String receiver, String msg) {
        String message = String.format("[ %s ] to [ %s ]: %s", sender.getNickname(), receiver, msg);
        for (ClientHandler c : clients) {
            if (c.getNickname().equals(receiver)) {
                c.sendMsg(message);
                if (!c.equals(sender)) {
                    sender.sendMsg(message);
                }
                return;
            }
        }
        sender.sendMsg("not found user: " + receiver);
    }
    
    public void subscribe(ClientHandler clientHandler) {
        clients.add(clientHandler);
        broadcastClientlist();
    }
    
    public void unsubscribe(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        broadcastClientlist();
    }
    
    public AuthService getAuthService() {
        return authService;
    }
    
    public boolean isLoginAuthenticated(String login) {
        for (ClientHandler c : clients) {
            if (c.getLogin().equals(login)) {
                return true;
            }
        }
        return false;
    }
    
    public void broadcastClientlist() {
        StringBuilder sb = new StringBuilder(Command.CLIENT_LIST);
        
        for (ClientHandler c : clients) {
            sb.append(" ").append(c.getNickname());
        }
        
        String msg = sb.toString();
        
        for (ClientHandler c : clients) {
            c.sendMsg(msg);
        }
    }
    
    private void createConnection() throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:main.db");
        statement = connection.createStatement();
    }
    
    private void disconnect() {
        try {
            System.out.println("Statement closed");
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        try {
            System.out.println("Connection closed");
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
