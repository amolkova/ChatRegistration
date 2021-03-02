package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import commands.Command;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private int maxTime;
    private String nickname;
    private String login;
    private int noTimeout = 0;
    
    public ClientHandler(Server server, Socket socket) {
        maxTime = 8000;
        try {
            this.server = server;
            this.socket = socket;
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            
            new Thread(() -> {
                try {
                    
                    // authentication cycle
                    while (true) {
                        String str = in.readUTF();
                        
                        // if the command disconnects
                        if (str.equals(Command.END)) {
                            out.writeUTF(Command.END);
                            throw new RuntimeException("the client wants to disconnect");
                        }
                        
                        // if command authentication
                        if (str.startsWith(Command.AUTH)) {
                            String[] token = str.split("\\s", 3);
                            if (token.length < 3) {
                                continue;
                            }
                            String newNick = server.getAuthService().getNicknameByLoginAndPassword(token[1], token[2]);
                            login = token[1];
                            if (newNick != null) {
                                if (!server.isLoginAuthenticated(login)) {
                                    nickname = newNick;
                                    sendMsg(Command.AUTH_OK + " " + nickname);
                                    server.subscribe(this);
                                    System.out.println("client: " + socket.getRemoteSocketAddress()
                                        + " connected with nick: " + nickname);
                                    // setting socket timeout = 0 , because we have authorization
                                    socket.setSoTimeout(noTimeout);
                                    break;
                                } else {
                                    sendMsg("this account is already in use");
                                }
                            } else {
                                // setting socket timeout
                                socket.setSoTimeout(maxTime);
                                sendMsg("wrong username / password ");
                                sendMsg("if you are not logged in, you will be disconnected after " + maxTime / 1000
                                    + " seconds");
                            }
                        }
                        
                        // if command registration
                        if (str.startsWith(Command.REG)) {
                            String[] token = str.split("\\s", 4);
                            if (token.length < 4) {
                                continue;
                            }
                            boolean regSuccess = server.getAuthService().registration(token[1], token[2], token[3]);
                            if (regSuccess) {
                                sendMsg(Command.REG_OK);
                            } else {
                                sendMsg(Command.REG_NO);
                            }
                        }
                        
                    }
                    // Working case
                    while (true) {
                        String str = in.readUTF();
                        
                        if (str.startsWith("/")) {
                            if (str.equals(Command.END)) {
                                out.writeUTF(Command.END);
                                break;
                            }
                            
                            if (str.startsWith(Command.PRIVATE_MSG)) {
                                String[] token = str.split("\\s", 3);
                                if (token.length < 3) {
                                    continue;
                                }
                                server.privateMsg(this, token[1], token[2]);
                            }
                        } else {
                            server.broadcastMsg(this, str);
                        }
                    }
                    // SocketTimeoutException
                } catch (SocketTimeoutException e) {
                    System.out.println("Error: The waiting time is over " + maxTime);
                    sendMsg("Error: The waiting time is over " + maxTime);
                    
                } catch (RuntimeException e) {
                    System.out.println(e.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    server.unsubscribe(this);
                    System.out.println(
                        "Client disconnected: " + (nickname == null ? socket.getRemoteSocketAddress() : nickname));
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
    
    public void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public String getNickname() {
        return nickname;
    }
    
    public String getLogin() {
        return login;
    }
}
