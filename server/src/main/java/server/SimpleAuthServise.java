package server;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SimpleAuthServise implements AuthService {
    private Statement statement;
    
    public SimpleAuthServise(Statement statement) {
        this.statement = statement;
    }
    
    @Override
    public String getNicknameByLoginAndPassword(String login, String password) throws SQLException {
        ResultSet data = statement.executeQuery("SELECT login, pass, nick FROM users WHERE login = '" + login + "'");
        
        if (data.getString(2).equals(password)) {
            String nick = data.getString(3);
            data.close();
            return nick;
        }
        data.close();
        return null;
    }
    
    @Override
    public boolean registration(String login, String password, String nickname) throws SQLException {
        statement.executeUpdate(
            "INSERT INTO users (login, pass, nick) VALUES ('" + login + "', '" + password + "', '" + nickname + "')");
        return true;
    }
}
