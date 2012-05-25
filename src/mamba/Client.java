package mamba;

import java.nio.channels.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.tree.*;

public class Client implements ActionListener {
    public int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    public boolean isWindows = System.getProperty("os.name").startsWith("Windows") ? true : false;
    public String downloadDir = System.getProperty("user.home") + "/Downloads";
    private static JFrame frame;
    protected static DefaultMutableTreeNode root;
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
	root = new DefaultMutableTreeNode("");
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
	    }
	    try {
		this.data = new ClientData(new InetSocketAddress(InetAddress.getByName(url), 8080));
	    } catch(UnknownHostException ex) {
		ex.printStackTrace();
	    } finally {
		this.data.start();
	    }
	}
    }

    protected static boolean login(SocketChannel socket) {
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
	    try {
		socket.write(ByteBuffer.wrap("Username:\\x0c".getBytes("UTF-8")));
		socket.write(ByteBuffer.wrap((((JTextField) options[1]).getText() + "\\x0c").getBytes("UTF-8")));
		socket.write(ByteBuffer.wrap("\\x0c\\x0c".getBytes("UTF-8")));
		MessageDigest digest = MessageDigest.getInstance("MD5");
		digest.update((new String(((JPasswordField) options[3]).getPassword())).getBytes("UTF-8"));
		socket.write(ByteBuffer.wrap("Password:\\x0c".getBytes("UTF-8")));
		socket.write(ByteBuffer.wrap(digest.digest()));
		socket.write(ByteBuffer.wrap("\\x0c\\x0c".getBytes("UTF-8")));
	    } catch(IOException ex) {
		ex.printStackTrace();
		return false;
	    } catch(NoSuchAlgorithmException ex) {
		ex.printStackTrace();
	    }
	}
	root.setUserObject(((JTextField) options[1]).getText());
	return true;
    }

    protected static void alert(ByteBuffer data) {
	JOptionPane.showMessageDialog(frame,
				      (((ByteBuffer) data.flip()).asCharBuffer().toString()));
    }

    protected static DefaultMutableTreeNode addTreeNode(String name, DefaultMutableTreeNode parent) {
	DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(name);
	parent.add(newNode);
	return newNode;
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
		    String bufstring = ((ByteBuffer) buffer.flip()).asCharBuffer().toString();
		    buffer.clear();
		    if(bufstring.equals("Please Login:\\x0c")) {
			if(!Client.login(this.socket)) {
			    throw new IOException();
			}
		    } else if(bufstring.equals("File:\\x0c")) {
			// Assume server will correctly send filename
			socket.read(buffer);
			Path file = Files.createFile(Paths.get(((ByteBuffer) buffer.flip()).asCharBuffer().toString()));
			while(!bufstring.equals("\\x0c\\x0c")) {
			    buffer.clear();
			    if(socket.read(buffer) != -1) {
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				while(buffer.hasRemaining()) {
				    bytes.write(buffer.get());
				}
				Files.write(file, bytes.toByteArray());
			    } else {
				// Either server messed up or connection went away
			    }
			}
		    } else if(bufstring.equals("Directory:\\x0c")) {
			socket.read(buffer);
			Path dir = Files.createFile(Paths.get(((ByteBuffer) buffer.flip()).asCharBuffer().toString()));
			while(!bufstring.equals("\\x0c\\x0c")) {
			    buffer.clear();
			    if(socket.read(buffer) != -1) {
				bufstring = ((ByteBuffer) buffer.flip()).asCharBuffer().toString();
				if(bufstring.equals("File:\\x0c")) {
				    
				} else if(bufstring.equals("Directory:\\x0c")) {
				    
				} else {
				    System.err.println("Unknown command: " + bufstring);
				}
			    } else {
				// Either server messed up or connection went away
			    }
			}
		    } else if(bufstring.equals("Show File:\\x0c")) {
			if(socket.read(buffer) != -1) {
			    String location = ((ByteBuffer) buffer.flip()).asCharBuffer().toString();
			    buffer.clear();
			    //Client.addTreeNode(
			} else {
			    // Either server messed up or connection went away
			}
		    } else if(bufstring.equals("Show Directory:\\x0c")) {
			
		    } else if(bufstring.equals("Alert:\\x0c")) {
			if(socket.read(buffer) != -1) {
			    Client.alert(buffer);
			} else {
			    // Either server messed up or connection went away
			}
		    } else {
			System.err.println("Unknown command: " + bufstring);
		    }
		    buffer.clear();
		}
	    }
	    this.socket.close();
	} catch(NullPointerException ex) {
	    ex.printStackTrace();
	} catch(IOException ex) {
	    ex.printStackTrace();
	} catch(InterruptedException ex) {
	    // Die, thread, die!
	    // But let's print a stack trace for fun, okay?
	    ex.printStackTrace();
	}
    }
    
    
}