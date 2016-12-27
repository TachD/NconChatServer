package sample;

import com.sun.org.apache.bcel.internal.generic.NEW;
import edu.BarSU.NcoN.MailSend.TLSSender;
import edu.BarSU.Ncon.Chat.NcoNServer;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

public class Controller {
    private static class MailSender {
        private static TLSSender TLSSenderObject = new TLSSender("SuppNcoN@gmail.com", "NcoNSupportPass");

        public static void SendRegisterMail(String RegEmail, String RegLName, String RegFName, String ValidCode) {
            String RegMessage = "Hello, " + RegLName + " " + RegFName + "\n" +
                    "Welcome to your new NcoN account!\n" +
                    "In to verify your e-mail, please input this code " +
                    "in register form:\n" +
                    "Valid code: " + ValidCode +
                    "\n\n" +
                    "Best regards,\n" +
                    "NcoN team!";

            TLSSenderObject.send("Registration", RegMessage, RegEmail);
        }

        public static void SendRecoveryMail(String Nick, String Email, String NewPass) {
            String RegMessage = "Hello, " + Nick + "\n" +
                    "Your NcoN account is recovered.\n" +
                    "You can log in and start using your" +
                    " account with next data: \n" +
                    "Nickname: " + Nick +
                    "\nPassword: " + NewPass +
                    "\n\n" +
                    "Best regards,\n" +
                    "NcoN team!";

            TLSSenderObject.send("Recovery", RegMessage, Email);
        }
    }

    // JavaFX Components
    private ArrayList<NcoNServer> SessionList = new ArrayList<>();

    private ServerSocket MainSocket;

    private volatile boolean NeedClose;

    // Database statement
    private Connection DBConn;

    private Statement DBStmnt;

    // .FXML elements
    @FXML
    private Label NameHost;

    @FXML
    private TextArea TAreaLog;

    @FXML
    private ComboBox<String> BDBType;

    @FXML
    private TextField TFeldHost;

    @FXML
    private TextField TFieldServise;

    @FXML
    private TextField TFieldUser;

    @FXML
    private PasswordField PFieldPass;

    @FXML
    private TextField TFieldPort;

    private String getEncryptedString(String SourceString) throws NoSuchAlgorithmException {
        final MessageDigest MD = MessageDigest.getInstance("SHA-256");

        MD.reset();
        MD.update(SourceString.getBytes(Charset.forName("UTF8")));

        return String.format("%064x", new BigInteger(1, MD.digest()));
    }

    private String[] ValidCode() {
        String[] Temp = new String[2];

        Random Rnd = new Random();

        Temp[0] = new String();

        for (int i = 0; i < 3; ++i)
            Temp[0] += Rnd.nextInt()%59 + 63;

        try {
            Temp[1] = getEncryptedString(Temp[0]);
            return Temp;
        } catch (NoSuchAlgorithmException NSAEx) {
            System.out.println("Password encryption error! " + NSAEx.getMessage());
            return null;
        }
    }

    private void GetServer(int PORT) {

        boolean IsConnectedRequest = false;

        if (SessionList.size() > 0)
            for (NcoNServer Temp: SessionList)
                if (Temp.getPort() == PORT) {
                    IsConnectedRequest = true;
                    break;
                }


        if (!IsConnectedRequest)
            if(SessionList.add(ListenClient(PORT))) {
                TAreaLog.appendText("P " + PORT + ". Start NcoN server\n");
                SessionList.get(SessionList.size() - 1).UpServer();
            }
            else
                TAreaLog.appendText("P " + PORT + ". Error NcoN server starting\n");
    }

    private NcoNServer ListenClient(int PORT) {
        NcoNServer TempStatementServer = new NcoNServer(PORT);

        try {
            TempStatementServer.listen();
        } catch (IOException IOEx) {
            TAreaLog.appendText("P" + PORT + ". NcoN Server creating error! " + IOEx.getMessage() + "\n");
            return null;
        }

        return TempStatementServer;
    }

    @FXML
    private void ConnectionToDataBase() {
        String ConnectURL;

        Locale.setDefault(Locale.ENGLISH);

        ConnectURL = (BDBType.getValue() == "Oracle")?
                "jdbc:oracle:thin:@//":"jdbc:mysql://";

        ConnectURL += TFeldHost.getText() + ":" + TFieldPort.getText() + "/" + TFieldServise.getText();

        try {
            DBConn = DriverManager.getConnection(ConnectURL, TFieldUser.getText(), PFieldPass.getText());

            DBStmnt = DBConn.createStatement();
        } catch (SQLException SQLEx) {
            TAreaLog.appendText("Connect to database not completed!\n");
            TAreaLog.appendText(SQLEx.getMessage());
            return;
        }
        TAreaLog.appendText("Connect to database completed!\n");
    }

    @FXML
    private void initialize() throws Exception {
        NameHost.setText("Host name: " + InetAddress.getLocalHost().toString());

        BDBType.getItems().addAll("Oracle", "My SQL");

        BDBType.setValue("Oracle");
    }

    @FXML
    private void DownServer() {
        TAreaLog.appendText("Server downing...\n");
        NeedClose = true;

        for (int i = 0; i < SessionList.size(); ++i) {
            TAreaLog.appendText("Server in port " + SessionList.get(i).getPort() + " was closed!\n");
            SessionList.get(i).CloseStream();
            SessionList.remove(i);
        }

        System.gc();

        try {
            DBStmnt.close();
            DBConn.close();
            TAreaLog.appendText("Database disconnected!\n");

            try {
                Socket ClosedSocket = new Socket(InetAddress.getLocalHost(), 10001);

                ClosedSocket.close();

                MainSocket.close();
            } catch (UnknownHostException UHEx) {
            } catch (IOException IOEx) {
                TAreaLog.appendText("Main Socket closed error!\n\n");
            }

            TAreaLog.appendText("Server down access!\n\n");
        } catch (SQLException SQLEx) {
            TAreaLog.appendText("Downing server error! " + SQLEx.getMessage() + "\n\n");
        }
    }

    private void Auth(Socket CSock, ObjectInputStream IS)
            throws IOException, ClassNotFoundException, SQLException {

        String Login    = IS.readObject().toString();
        String Password = IS.readObject().toString();

        ResultSet UsersData = DBStmnt.executeQuery("SELECT NICKNAME, EMAIL, FIRST_NAME, DATEOFBIRTH, LAST_NAME " +
                "FROM USERS WHERE NICKNAME = '" + Login    + "' " +
                             "AND PASSWORD = '" + Password + "'");

        ObjectOutputStream OS = new ObjectOutputStream(CSock.getOutputStream());

        if (UsersData.next()) {
            for (int i = 1; i < 6; ++i)
                OS.writeObject(UsersData.getString(i));

            TAreaLog.appendText("User " + Login + " is online!\n");
        }
        else
            OS.writeObject("0");

        OS.close();
    }

    private void Validation(Socket CSock, ObjectInputStream IS)
            throws ClassNotFoundException, IOException, SQLException {

        String RegNick  = IS.readObject().toString();
        String RegFName = IS.readObject().toString();
        String RegLName = IS.readObject().toString();
        String RegEmail = IS.readObject().toString();

        ResultSet UsersData = DBStmnt.executeQuery("SELECT NICKNAME FROM USERS WHERE " +
                "NICKNAME = '" + RegNick + "'");

        ObjectOutputStream OS = new ObjectOutputStream(CSock.getOutputStream());

        if (UsersData.next()) {
            OS.writeObject(-1);

            OS.close();
            return;
        }

        UsersData = DBStmnt.executeQuery("SELECT EMAIL FROM USERS WHERE " +
                "EMAIL = '" + RegEmail + "'");

        if (UsersData.next()) {
            OS.writeObject(-2);

            OS.close();
            return;
        }

        String[] ValidCode = ValidCode();

        MailSender.SendRegisterMail(RegEmail, RegLName, RegFName, ValidCode[0]);

        TAreaLog.appendText("Sent a message with validation\n\t to " + RegEmail + '\n');

        OS.writeObject(ValidCode[1]);

        OS.close();
    }

    private void AccountRegistration(Socket CSock, ObjectInputStream IS)
            throws IOException, ClassNotFoundException {

        String RegNick   = IS.readObject().toString();
        String RegPass   = IS.readObject().toString();
        String RegFName  = IS.readObject().toString();
        String RegLName  = IS.readObject().toString();
        String RegEmail  = IS.readObject().toString();
        String RegDBirth = IS.readObject().toString();

        ObjectOutputStream OS = new ObjectOutputStream(CSock.getOutputStream());

        boolean isRegistered;
        try {

            isRegistered = DBStmnt.execute(
                    "INSERT INTO USERS(NICKNAME, PASSWORD, EMAIL, FIRST_NAME, DATEOFBIRTH, LAST_NAME) " +
                            "VALUES ('" + RegNick + "','" + RegPass + "','" + RegEmail +
                            "','" + RegFName + "','" + RegDBirth + "','" + RegLName + "')");
        } catch (SQLException SQLEx) {
            TAreaLog.appendText("Account not created! SQL Error!\n");
            OS.writeObject(0);

            OS.close();
            return;
        }

        if (isRegistered) {
            TAreaLog.appendText("Account " + RegNick + " created!" + '\n');
            OS.writeObject(1);
        }
        else {
            TAreaLog.appendText("Account " + RegNick + " not created!" + '\n');
            OS.writeObject(0);
        }

        OS.close();
    }

    private void Recovery(Socket CSock, ObjectInputStream IS)
            throws IOException, ClassNotFoundException, SQLException, NoSuchAlgorithmException {
        String Email = IS.readObject().toString();

        ResultSet UsersData = DBStmnt.executeQuery("SELECT NICKNAME, PASSWORD FROM USERS WHERE " +
                "EMAIL = '" + Email + "'");

        ObjectOutputStream OS = new ObjectOutputStream(CSock.getOutputStream());

        if (!UsersData.next()) {
            OS.writeObject(-1);
            OS.close();
            IS.close();
            CSock.close();
            return;
        }

        // Message with recovery data
        String Nick = UsersData.getString(1);
        String OldPass = UsersData.getString(2);
        String NewPass = getEncryptedString(OldPass.substring(0, 6));

        TAreaLog.appendText("Recovering account " + Nick + "...\n");


        DBStmnt.execute("UPDATE USERS " +
                "SET PASSWORD ='" + NewPass +
                "' WHERE EMAIL ='" + Email + "'");

        MailSender.SendRecoveryMail(Nick, Email, OldPass.substring(0, 6));

        OS.writeObject(0);

        OS.close();
    }

    @FXML
    private void UpServer() {
        try {
            if (DBConn == null || DBConn.isClosed()) {
                TAreaLog.appendText("Connect to the database and try again.\n");
                return;
            }
        } catch (SQLException SQLEx) {}


            NeedClose = false;

        new Thread(new Runnable() {
            @Override
            public void run() {
                TAreaLog.appendText("Server System starting...\n");

                try {
                    if (MainSocket == null)
                        MainSocket = new ServerSocket(10001);
                } catch (IOException IOEx) {
                    TAreaLog.appendText("Error Main Server Socket creating\n");
                }

                while (true)
                    try {
                        if (NeedClose)
                            throw new Exception();

                        Socket CSock = MainSocket.accept();

                        ObjectInputStream IS = new ObjectInputStream(CSock.getInputStream());
                        int PORT = Integer.valueOf(IS.readObject().toString());

                        switch (PORT) {
                            case -1:
                                Auth(CSock, IS);
                                break;
                            case -2:
                                Validation(CSock, IS);
                                break;
                            case -3:
                                AccountRegistration(CSock, IS);
                                break;
                            case -4:
                                Recovery(CSock, IS);
                                break;
                            default:
                                boolean IsUsed = false;

                                if (SessionList.size() != 0)
                                    for (NcoNServer TempPort : SessionList)
                                        if (TempPort.getPort() == PORT) {
                                            IsUsed = true;
                                            break;
                                        }

                                if (!IsUsed)
                                    GetServer(PORT);
                        }

                        IS.close();
                        CSock.close();

                    } catch (Exception Ex) {
                        TAreaLog.appendText("Server System closed...\n");

                        try {
                            MainSocket.close();
                            MainSocket = null;
                        } catch (IOException IOEx) {
                            TAreaLog.appendText("Main socket closed error!\n");
                        }
                        break;
                    }
                }
        }, "Main_listener").start();

    }

    @FXML
    private void ClearWinLog() {
        TAreaLog.clear();
    }

    @FXML
    private void GetMonitoringData() {
        TAreaLog.appendText("\n\tServer Monitoring\n");

        TAreaLog.appendText("Database status: ");
        try {
            if (DBConn != null && !DBConn.isClosed())
                TAreaLog.appendText("connected\n\n");
            else
                TAreaLog.appendText("disconnected\n\n");
        } catch (SQLException SQLEx) {
            TAreaLog.appendText("Monitoring data getting error! "
                    + SQLEx.getMessage() + "\n\n");
        }

        TAreaLog.appendText("Main Server Socket status: ");
            if (MainSocket != null && !MainSocket.isClosed())
                TAreaLog.appendText("listen\n\n");
            else
                TAreaLog.appendText("closed\n\n");

        if (SessionList.size() == 0)
            TAreaLog.appendText("No open server ports!\n");

        for (int i = 0; i < SessionList.size(); ++i)
            TAreaLog.appendText((i + 1) + ": P" + SessionList.get(i).getPort() + " status: " +
                    SessionList.get(i).IsActive() + "\n");

        TAreaLog.appendText("\n");
    }
}
