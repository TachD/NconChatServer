package sample;

import edu.BarSU.NcoN.MailSend.TLSSender;

import edu.BarSU.Ncon.Chat.Server.NcoNServer;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.math.BigInteger;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.LocalTime;
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

    private ArrayList<NcoNServer> SessionList = new ArrayList<>();

    private ArrayList<String> PortList = new ArrayList<>();

    private ArrayList<String> OnlineList = new ArrayList<>();

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

    private String TimeLog() {
        LocalTime NowTime = LocalTime.now();

        String TimeLogMessage = "[";

        if (NowTime.getHour() < 10)
            TimeLogMessage += "0";

        TimeLogMessage += NowTime.getHour() + ":";

        if (NowTime.getMinute() < 10)
            TimeLogMessage += "0";

        TimeLogMessage += NowTime.getMinute() + "] ";

        return TimeLogMessage;
    }

    private String getEncryptedString(String SourceString)
            throws NoSuchAlgorithmException {
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
        } catch (NoSuchAlgorithmException NSAEx) {
            System.out.println("Password encryption error! " + NSAEx.getMessage());
            return null;
        }

        return Temp;
    }

    private void GetServer(int PORT) {

        boolean isConnectedRequest = false;

        for (String Temp: PortList)
            if (Temp.equals(String.valueOf(PORT))) {
                isConnectedRequest = true;
                break;
            }

        if (!isConnectedRequest)
            if(SessionList.add(listenClient(PORT))) {
                TAreaLog.appendText(TimeLog() + "P " + PORT + ". Start NcoN server\n");
                SessionList.get(SessionList.size() - 1).UpServer();

                PortList.add(String.valueOf(PORT));
            }
            else
                TAreaLog.appendText(TimeLog() + "P " + PORT + ". Error NcoN server starting\n");
    }

    private NcoNServer listenClient(int PORT) {
        NcoNServer TempStatementServer = new NcoNServer(PORT);

        TempStatementServer.listen();

        return TempStatementServer;
    }

    private void Auth(ObjectInputStream IS, ObjectOutputStream OS, Crypto CryptoObj)
            throws Exception {

        String Login    = CryptoObj.readDecryptString(IS);
        String Password = CryptoObj.readDecryptString(IS);

        ResultSet UsersData = DBStmnt.executeQuery("SELECT NICKNAME, EMAIL, FIRST_NAME, DATEOFBIRTH, LAST_NAME " +
                "FROM USERS WHERE NICKNAME = '" + Login    + "' " +
                             "AND PASSWORD = '" + Password + "'");


        OS.writeObject(CryptoObj.genKey());

        if (UsersData.next()) {
            for (int i = 1; i < 6; ++i)
                CryptoObj.sendEncryptString(OS, UsersData.getString(i));

            TAreaLog.appendText(TimeLog() + "User " + Login + " is online!\n");

            OnlineList.add(Login);
        }
        else
            CryptoObj.sendEncryptString(OS, "0");
    }

    private void Validation(ObjectInputStream IS, ObjectOutputStream OS, Crypto CryptoObj)
            throws Exception {

        String RegNick  = CryptoObj.readDecryptString(IS);
        String RegFName = CryptoObj.readDecryptString(IS);
        String RegLName = CryptoObj.readDecryptString(IS);
        String RegEmail = CryptoObj.readDecryptString(IS).toLowerCase();

        ResultSet UsersData = DBStmnt.executeQuery("SELECT NICKNAME FROM USERS WHERE " +
                "NICKNAME = '" + RegNick + "'");

        OS.writeObject(CryptoObj.genKey());

        if (UsersData.next()) {
            CryptoObj.sendEncryptString(OS, "-1");

            return;
        }

        UsersData = DBStmnt.executeQuery("SELECT EMAIL FROM USERS WHERE " +
                "EMAIL = '" + RegEmail + "'");

        if (UsersData.next()) {
            CryptoObj.sendEncryptString(OS, "-2");

            return;
        }

        String[] ValidCode = ValidCode();

        MailSender.SendRegisterMail(RegEmail, RegLName, RegFName, ValidCode[0]);

        TAreaLog.appendText(TimeLog() + "Sent a message with validation\n\t to " + RegEmail + '\n');

        CryptoObj.sendEncryptString(OS, ValidCode[1]);
    }

    private void AccountRegistration(ObjectInputStream IS, ObjectOutputStream OS, Crypto CryptoObj)
            throws Exception {

        String RegNick   = CryptoObj.readDecryptString(IS);
        String RegPass   = CryptoObj.readDecryptString(IS);
        String RegFName  = CryptoObj.readDecryptString(IS);
        String RegLName  = CryptoObj.readDecryptString(IS);
        String RegEmail  = CryptoObj.readDecryptString(IS).toLowerCase();
        String RegDBirth = CryptoObj.readDecryptString(IS);

        try {
            OS.writeObject(CryptoObj.genKey());

            DBStmnt.execute(
                    "INSERT INTO USERS(NICKNAME, PASSWORD, EMAIL, FIRST_NAME, DATEOFBIRTH, LAST_NAME) " +
                            "VALUES ('" + RegNick + "','" + RegPass + "','" + RegEmail +
                            "','" + RegFName + "','" + RegDBirth + "','" + RegLName + "')");
        } catch (SQLException SQLEx) {
            TAreaLog.appendText(TimeLog() + "Account " + RegNick + " not created!" + '\n');
            CryptoObj.sendEncryptString(OS, "0");
            return;
        }
            TAreaLog.appendText(TimeLog() + "Account " + RegNick + " not created!" + '\n');
            CryptoObj.sendEncryptString(OS, "0");
    }

    private void Recovery(ObjectInputStream IS, ObjectOutputStream OS, Crypto CryptoObj)
            throws Exception {

        String Email = CryptoObj.readDecryptString(IS).toLowerCase();

        ResultSet UsersData = DBStmnt.executeQuery("SELECT NICKNAME, PASSWORD FROM USERS WHERE " +
                    "EMAIL = '" + Email + "'");

        OS.writeObject(CryptoObj.genKey());

        if (UsersData == null || !UsersData.next()) {
            CryptoObj.sendEncryptString(OS, "-1");

            return;
        }

        // Message with recovery data
        String Nick = UsersData.getString(1);
        String OldPass = UsersData.getString(2);
        String NewPass = getEncryptedString(OldPass.substring(0, 6));

        TAreaLog.appendText(TimeLog() + "Recovering account " + Nick + "...\n");


        DBStmnt.execute("UPDATE USERS " +
                "SET PASSWORD ='" + NewPass +
                "' WHERE EMAIL ='" + Email + "'");

        MailSender.SendRecoveryMail(Nick, Email, OldPass.substring(0, 6));

        CryptoObj.sendEncryptString(OS, "0");
    }

    private void Logout(ObjectInputStream IS, Crypto CryptoObj)
            throws Exception {

        String Login = CryptoObj.readDecryptString(IS);

        TAreaLog.appendText(TimeLog() + "User " + Login + " is offline!\n");

        OnlineList.remove(Login);
    }

    @FXML
    private void UpServer() {
        try {
            if (DBConn == null || DBConn.isClosed()) {
                TAreaLog.appendText(TimeLog() + "Connect to the database and try again.\n");
                return;
            }
        } catch (SQLException SQLEx) {}


            NeedClose = false;

        new Thread(new Runnable() {
            @Override
            public void run() {
                TAreaLog.appendText(TimeLog() + "Server System starting...\n");

                SocketAddress SockAddr = new InetSocketAddress("0.0.0.0", 10001);
                try {
                    if (MainSocket == null) {
                        MainSocket = new ServerSocket();
                        MainSocket.bind(SockAddr);
                    }
                } catch (IOException IOEx) {
                    TAreaLog.appendText(TimeLog() + "Error Main Server Socket creating\n");
                }

                while (true)
                    try {
                        if (NeedClose)
                            throw new Exception();

                        Crypto CryptoObj = null;

                        ObjectOutputStream OS = null;

                        Socket CSock = MainSocket.accept();

                        ObjectInputStream IS = new ObjectInputStream(CSock.getInputStream());

                        int PORT = Integer.valueOf(IS.readObject().toString());

                        if (PORT < 0 && PORT > -5)
                            OS = new ObjectOutputStream(CSock.getOutputStream());

                        if (PORT < 0 && PORT > -6) {
                            CryptoObj = new Crypto();
                            CryptoObj.setKey((Key) IS.readObject());
                        }


                        switch (PORT) {
                            case -1:
                                Auth(IS, OS, CryptoObj);
                                break;
                            case -2:
                                Validation(IS, OS, CryptoObj);
                                break;
                            case -3:
                                AccountRegistration(IS, OS, CryptoObj);
                                break;
                            case -4:
                                Recovery(IS, OS, CryptoObj);
                                break;
                            case -5:
                                Logout(IS, CryptoObj);
                                break;
                            default:
                                boolean IsUsed = false;

                                if (SessionList.size() != 0)
                                    for (NcoNServer TempServer : SessionList)
                                        if (TempServer.getPort() == PORT) {
                                            IsUsed = true;
                                            break;
                                        }

                                if (!IsUsed)
                                    GetServer(PORT); // Crypto

                        }

                        if (OS != null)
                            OS.close();

                        if (IS != null)
                            IS.close();

                        if (CSock != null)
                            CSock.close();

                    } catch (Exception Ex) {
/*                        TAreaLog.appendText(TimeLog() + "Server System closed...\n");

                        try {
                            MainSocket.close();
                        } catch (IOException IOEx) {
                            TAreaLog.appendText(TimeLog() + "Main socket closed error!\n");
                        }
*/
  //                      break;
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

        TAreaLog.appendText("\t" + TimeLog() + "\n\n");
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
                    SessionList.get(i).isActive() + "\n");

        TAreaLog.appendText("\n");

        TAreaLog.appendText((OnlineList.size() == 0)?
                "No user online!\n":"User online list:!\n");

        for (String Nick: OnlineList)
            TAreaLog.appendText("\t" + Nick + "\n");

        if (OnlineList.size() != 0)
            TAreaLog.appendText("\tTotal online: " + OnlineList.size() + "\n\n");
    }

    @FXML
    private void ConnectionToDataBase() {
        Locale.setDefault(Locale.ENGLISH);

        String ConnectURL = (BDBType.getValue() == "Oracle")?
                "jdbc:oracle:thin:@//":"jdbc:mysql://";

        ConnectURL += TFeldHost.getText() + ":" + TFieldPort.getText() + "/" + TFieldServise.getText();

        try {
            DBConn = DriverManager.getConnection(ConnectURL, TFieldUser.getText(), PFieldPass.getText());

            DBStmnt = DBConn.createStatement();
        } catch (SQLException SQLEx) {
            TAreaLog.appendText(TimeLog() + "Connect to database not completed!\n");
            TAreaLog.appendText(SQLEx.getMessage());

            return;
        }
        TAreaLog.appendText(TimeLog() + "Connect to database completed!\n");
    }

    @FXML
    private void initialize() throws Exception {
        NameHost.setText("Host name: " + InetAddress.getLocalHost().toString());

        BDBType.getItems().addAll("Oracle", "My SQL");

        BDBType.setValue("Oracle");

        ConnectionToDataBase();
        UpServer();
    }

    @FXML
    private void DownServer() {
        TAreaLog.appendText(TimeLog() + "Server downing...\n");
        NeedClose = true;

        for (int i = 0; i < SessionList.size(); ++i) {
            TAreaLog.appendText("Server in port " + SessionList.get(i).getPort() + " was closed!\n");
            SessionList.get(i).CloseStream();
            SessionList.remove(i);
        }

        PortList.clear();
        OnlineList.clear();

        try {
            if (!DBStmnt.isClosed())
                DBStmnt.close();

            if (!DBConn.isClosed())
                DBConn.close();

            TAreaLog.appendText(TimeLog() + "Database disconnected!\n");

            try {
                SocketAddress SockAddr = new InetSocketAddress(InetAddress.getLocalHost() ,10001);
                Socket CloserSocket = new Socket();

                CloserSocket.connect(SockAddr);

                CloserSocket.close();

                MainSocket.close();
            } catch (UnknownHostException UHEx) {
            } catch (IOException IOEx) {
                TAreaLog.appendText("Main Socket closed error!\n\n");
            }

            TAreaLog.appendText(TimeLog() + "Server down access!\n\n");
        } catch (SQLException SQLEx) {
            TAreaLog.appendText("Downing server error! " + SQLEx.getMessage() + "\n\n");
        }
    }
}
