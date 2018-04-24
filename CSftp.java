import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Scanner;

public class CSftp
{
    // static variables
    static Socket clientSocket;
    static PrintWriter out;
    static BufferedReader in;

    static Socket passiveSocket;
    static PrintWriter passiveOut;
    static BufferedReader passiveIn;
    static InputStream inputStream;
    static String resCode;
    static boolean keepRunning = false;

    public static void main(String [] args)
    {
        // Get command line arguments and connected to FTP
        // If the arguments are invalid or there aren't enough of them
        // then exit with usage: cmd ServerAddress ServerPort

        // command loop goes here ....
        String hostName = null;
        int portNumber = 21;

        if (args.length == 1) {
            hostName = args[0];
        } else if (args.length == 2) {
            hostName = args[0];
            portNumber = Integer.parseInt(args[1]);
        } else {
            System.out.println("0x002 Incorrect number of arguments.");
            System.exit(1);
        }

        createSocket(hostName, portNumber, false);

        Scanner scanner = new Scanner(System.in);

        while (keepRunning) {
            System.out.print("csftp> ");
            try {
                String cmd = scanner.nextLine();

                String[] cmdArray =  cmd.split(" ");

                String arg1 = cmdArray[0];

                switch (arg1) {
                    case "user":
                        if(cmdArray.length != 2) {
                            System.out.println("0x002 Incorrect number of arguments");
                        } else {
                            sendCommandToServer("USER " + cmdArray[1]);
                            printServerResponse();
                        }
                        break;
                    case "pw":
                        if(cmdArray.length != 2) {
                            System.out.println("0x002 Incorrect number of arguments");
                        } else {
                            sendCommandToServer("PASS " + cmdArray[1]);
                            printServerResponse();
                        }
                        break;
                    case "quit":
                        if(cmdArray.length != 1) {
                            System.out.println("0x002 Incorrect number of arguments");
                        } else {
                            sendCommandToServer("QUIT");
                            printServerResponse();
                        }
                        break;
                    case "get":
                        if(cmdArray.length != 2) {
                            System.out.println("0x002 Incorrect number of arguments");
                        } else {
                            sendCommandToServer("PASV");
                            passiveMode("RETR", cmdArray[1]);
                        }
                        break;
                    case "features":
                        if(cmdArray.length != 1) {
                            System.out.println("0x002 Incorrect number of arguments");
                        } else {
                            sendCommandToServer("FEAT");
                            printServerResponse();
                        }
                        break;
                    case "cd":
                        if(cmdArray.length != 2) {
                            System.out.println("0x002 Incorrect number of arguments");
                        } else {
                            sendCommandToServer("CWD " + cmdArray[1]);
                            printServerResponse();
                        }
                        break;
                    case "dir":
                        if(cmdArray.length != 1) {
                            System.out.println("0x002 Incorrect number of arguments");
                        } else {
                            sendCommandToServer("PASV");
                            passiveMode("LIST", null);

                        }
                        break;
                    default:
                        System.out.println("0x001 Invalid command.");
                        break;
                }
            } catch(Exception e) {
                System.out.println("0xFFFE Input error while reading commands, terminating.");
                System.exit(1);
            }
        }
    }

    private static void sendCommandToServer(String command) {
        System.out.println("--> " + command);
        out.println(command);
        out.flush();
    }

    private static void printServerResponse() {
        try {
            String line = in.readLine();
            resCode = line.substring(0, 3);
            System.out.println("<-- " + line);

            if (line.charAt(3) == '-') {
                while (line.charAt(3) != ' ') {
                    line = in.readLine();
                    System.out.println("<-- " + line);
                }
            }
            checkResCode();
        } catch (IOException e) {
            System.out.println("0xFFFF Processing error. Failed to print server response.");
            System.exit(1);
        }
    }

    private static void createSocket(String hostName, int portNumber, boolean passiveMode) {
        try {
            if(passiveMode) {
                passiveSocket = new Socket();
                passiveSocket.connect(new InetSocketAddress(hostName, portNumber), 10000);
                passiveOut = new PrintWriter(passiveSocket.getOutputStream(), true);
                passiveIn = new BufferedReader(new InputStreamReader(passiveSocket.getInputStream()));
                inputStream = passiveSocket.getInputStream();
            } else {
                clientSocket = new Socket();
                clientSocket.connect(new InetSocketAddress(hostName, portNumber), 20000);
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                printServerResponse();
            }
        } catch (SocketTimeoutException e) {
            if(passiveMode) {
                System.out.println("0x3A2 Data transfer connection to " + hostName + "on port " + portNumber + " failed to open.");
            } else {
                System.out.println("0xFFFC Control connection to " + hostName + "on port " + portNumber + " failed to open.");
                System.exit(1);
            }
        } catch (IOException e) {
            System.out.println("0xFFFD Control connection I/O error, closing control connection.");
            System.exit(1);
        }
    }

    public static void passiveMode(String command, String fileName) {
        try {
            String serverResponse = in.readLine();
            String serverCode = serverResponse.substring(0,3);

            if(serverCode.equals("227")) {
                int fromIndex = serverResponse.indexOf("(") + 1;
                int toIndex = serverResponse.indexOf(")");
                String ipPortString = serverResponse.substring(fromIndex, toIndex);
                String[] ipPortArray = ipPortString.split(",");
                String ip = "";

                for (int i = 0; i < 4; i++) {
                    if (i == 0) {
                        ip = ipPortArray[i];
                    } else {
                        ip = ip + "." + ipPortArray[i];
                    }
                }

                int port = (Integer.parseInt(ipPortArray[4]) * 256) + Integer.parseInt(ipPortArray[5]);
                createSocket(ip, port, true);

                if (command.equals("RETR")) {
                    sendCommandToServer("RETR " + fileName);
                    printServerResponse();

                    if (resCode.equals("150")) {
                        File file = new File(fileName);
                        if(file.createNewFile()) {
                            FileOutputStream fileOut = new FileOutputStream(file);

                            int c;
                            while ((c = inputStream.read()) != -1) {
                                fileOut.write(c);
                            }
                            fileOut.flush();
                            fileOut.close();
                        }
                        printServerResponse();
                    } else if (resCode.equals("451") || resCode.equals("551") || resCode.equals("550")) {
                        System.out.println("0x38E Access to local file " + fileName +" denied.");
                    } else if (resCode.equals("425") || resCode.equals("426")) {
                        System.out.println("0x3A7 Data transfer connection I/O error, closing data connection.");
                        passiveSocket.close();
                        passiveOut.close();
                        passiveIn.close();
                        inputStream.close();
                    }
                } else {
                    sendCommandToServer("LIST");
                    printServerResponse();
                    if (resCode.equals("150") || resCode.equals("125")) {
                        String line = passiveIn.readLine();
                        while (line != null) {
                            System.out.println(line);
                            line = passiveIn.readLine();
                        }
                    } else if ((resCode.equals("425") || resCode.equals("426"))) {
                        System.out.println("0x3A7 Data transfer connection I/O error, closing data connection.");
                        passiveSocket.close();
                        passiveOut.close();
                        passiveIn.close();
                        inputStream.close();
                    }
                    printServerResponse();
                }
            }

        } catch (IOException e) {
            try {
                System.out.println("0x3A7 Data transfer connection I/O error, closing data connection");
                passiveSocket.close();
                passiveOut.close();
                passiveIn.close();
                inputStream.close();
            } catch(IOException e1) {
                System.out.println("0xFFFF Processing error. Failed to close passive socket.");
                System.exit(1);
            }
        }
    }

    private static void checkResCode() {
        switch(resCode) {
            case "220":
                keepRunning = true;
                break;
            case "221":
                try {
                    clientSocket.close();
                    keepRunning = false;
                } catch (IOException e) {
                    System.out.println("0xFFFF Processing error. Failed to close client socket.");
                    System.exit(1);
                }
                break;
            case "120":
                try {
                    clientSocket.close();
                    keepRunning = false;
                } catch (IOException e) {
                    System.out.println("0xFFFF Processing error. Failed to close client socket.");
                    System.exit(1);
                }
                break;
            case "421":
                try {
                    clientSocket.close();
                    keepRunning = false;
                } catch (IOException e) {
                    System.out.println("0xFFFF Processing error. Failed to close client socket.");
                    System.exit(1);
                }
                break;
        }
    }

}