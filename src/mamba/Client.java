package mamba;

import java.nio.channels.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.io.*;
import java.net.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.tree.*;

public class Client implements ActionListener {
    public int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    public boolean isWindows = System.getProperty("os.name").startsWith("Windows") ? true : false;
    public String downloadDir = System.getProperty("user.home") + "/Downloads";
    private JFrame frame;
    private ClientData data;

    Client() {
	frame = new JFrame("Mamba Client");
	frame.setSize(500, 500);
	frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	JMenuBar menubar = new JMenuBar();
	JMenu filemenu = new JMenu("File");
	filemenu.setMnemonic(KeyEvent.VK_F);
	JMenuItem filedown = new JMenuItem("Download", KeyEvent.VK_D);
	filedown.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, this.shortcut));
	JMenuItem fileup = new JMenuItem("Upload", KeyEvent.VK_U);
	fileup.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, this.shortcut));
	filemenu.add(filedown);
	filemenu.add(fileup);
	menubar.add(filemenu);
	JMenu gomenu = new JMenu("Go");
	gomenu.setMnemonic(KeyEvent.VK_G);
	JMenuItem goconnect = new JMenuItem("Connect To...", KeyEvent.VK_C);
	goconnect.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, this.shortcut));
	gomenu.add(goconnect);
	menubar.add(gomenu);
	filedown.addActionListener(this);
	fileup.addActionListener(this);
	goconnect.addActionListener(this);
	frame.setJMenuBar(menubar);
	ClientData data = new ClientData(new InetSocketAddress("http://localhost", 8080));
	data.start();
	DefaultMutableTreeNode root = new DefaultMutableTreeNode("");
	JTree tree = new JTree(root);
	JScrollPane scroll = new JScrollPane(tree);
	frame.getContentPane().add(scroll, BorderLayout.CENTER);
	frame.setVisible(true);
    }

    public void actionPerformed(ActionEvent event) {
	String command = event.getActionCommand();
	if(command.equals("Help")) {
	    
	} else if(command.equals("Connect To...")) {
	    String url = JOptionPane.showInputDialog("Enter URL");
	    if(url != null && url.length() != 0) {
		if(this.data != null) {
		    this.data.interrupt();
		    try {
			this.data.join();
		    } catch(InterruptedException ex) {
			// Ignore, needed to satisfy compiler
		    }
		}
		this.data = new ClientData(new InetSocketAddress(url, 8080));
	    }
	}
    }

    public void login(SocketChannel socket) {
	Object[] options = { new JLabel("Username"),
			     new JTextField(50),
			     new JLabel("Password"),
			     new JPasswordField(50),
			     "OK", "Cancel" };
	int response = JOptionPane.showOptionDialog(frame,
				     "Enter Login Information",
				     "Mamba Login",
				     JOptionPane.OK_CANCEL_OPTION,
				     JOptionPane.QUESTION_MESSAGE,
				     null,
				     options,
				     "OK");
	if(response == 0) {
	    socket.write(ByteBuffer.wrap(("Username:\v" + options[1].paramString() + "\v").getBytes("UTF-8")));
	    socket.write(ByteBuffer.wrap(("Password:\v" + new String(options[3].getPassword()) + "\v").getBytes("UTF-8")));
	}
    }

    public static void main(String[] args) {
	if(System.getProperty("os.name").startsWith("Mac OS X")) {
	    System.setProperty("apple.laf.useScreenMenuBar", "true");
	}
	SwingUtilities.invokeLater(new Runnable() {
		public void run() {
		    new Client();
		}
	    });
    }
}

class ClientData extends Thread {
    private SocketChannel socket;
    private InetSocketAddress address;

    ClientData(InetSocketAddress address) {
	this.address = address;
    }

    public void run() {
	try {
	    this.socket.open();
	    this.socket.connect(this.address);
	    while(this.socket.isConnected()) {
		if(Thread.interrupted()) {
		    throw new InterruptedException();
		}
		ByteBuffer buffer = ByteBuffer.allocate(2048);
		while(socket.read(buffer) != -1) {
		    String bufstring = ((buffer.flip()).asCharBuffer().toString();
		    if(bufstring.equals("Please Login:\v")) {
			login(this.socket);
		    } else if(bufstring.equals("File:\v")) {
			buffer.clear();
			// Assume server will correctly send filename
			Path file = Files.createFile(Paths.get(socket.read(buffer)));
			while(!bufstring.equals("\v")) {
			    buffer.clear();
			    if(socket.read(buffer) != -1) {
				byte[] bytes;
				while(buffer.hasRemaining()) {
				    bytes[bytes.length] = buffer.get();
				}
				Files.write(file, bytes);
			    } else {
				// Either server messed up or connected went away
			    }
			}
		    } else if(bufstring.equals("Directory:\v")) {
			
		    } else {
			System.err.println("Unknown command: " + bufstring);
		    }
		    buffer.clear();
		}
	    }
	    this.socket.close();
	} catch(IOException ex) {
	    ex.printStackTrace();
	} catch(InterruptedException ex) {
	    // Die, thread, die!
	    // But let's print a stack trace for fun, okay?
	    ex.printStackTrace();
	}
    }
    
    
}