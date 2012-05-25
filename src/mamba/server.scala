package mamba

import java.nio.channels._
import java.nio.file._
import java.nio.ByteBuffer
import java.io._
import java.net._
import scala.actors._
import scala.xml._
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._

object Server {
    var fileDir: String = ""
    val userList = XML.loadFile("/etc/mamba.d/users.xml")
    
    def main(args: Array[String]) = {
        try {
            this.loadConf()
            val server = ServerSocketChannel.open()
            server.configureBlocking(false)
            server.socket().bind(new InetSocketAddress(/*457*/8080))
            while(true) {
                var socket: SocketChannel = server.accept()
                if(socket != null) {
                    // Request
                    val request = new Request
                    request.start()
                    request ! socket
                }
            }
        } catch {
            case ex: IOException =>
                ex.printStackTrace()
        }
    }
    
    def loadConf(): Unit = {
        val data = this.readAll(new FileInputStream("/etc/mamba.conf").getChannel())
        var currentWord: String = ""
        var ignore: Boolean = false
        var inQuote: Boolean = false
        var prevChar: Char = '\0'
        var operation: Symbol = 'none
        def alphaFunc(ch: Char, ignore: Boolean): String = {
            if(ignore == false) {
                return ch.toString
            } else {
                return ""
            }
        }
        for((ch: Char) <- data) {
            ch match {
                case ch if 'a' until 'z' contains ch =>
                    currentWord += alphaFunc(ch, ignore)
                case ch if 'A' until 'Z' contains ch =>
                    currentWord += alphaFunc(ch, ignore)
                case ch if '0' until '9' contains ch | '_' =>
                    currentWord += alphaFunc(ch, ignore)
                case '#' =>
                    if(inQuote == true) {
                        currentWord += "#"
                    } else if(ignore == false) {
                        ignore = true
                    }
                case ' ' | '\t' =>
                    if(inQuote == true) {
                        currentWord += ch.toString()
                    } else if(operation != 'none) {
                        operation match {
                            case 'fileDir =>
                                fileDir = currentWord
                            case 'serverType =>
                                // For ze future
                        }
                    } else if(currentWord == "fileDir") {
                        operation = 'fileDir
                    }
                case '\n' =>
                    if(ignore == true) {
                        ignore = false
                    } else if(operation != 'none) {
                        operation = 'none
                    }
                case '"' =>
                    if(ignore == false) {
                        if(prevChar == '\\') {
                            currentWord += "\""
                        } else {
                            inQuote = !inQuote
                        }
                    }
                case '\\' =>
                    // ignore, but allow
                case _ =>
                    if(inQuote == true) {
                        currentWord += ch.toString
                    } else if(ignore == false) {
                        // Do nothing for now, but throw error later
                    }
            }
            prevChar = ch
        }
    }
    
    private def readAll(file: FileChannel): String = {
        var result: String = ""
        var buffer: ByteBuffer = ByteBuffer.allocate(1024)
        while(file.read(buffer) != -1) {
            result += ((buffer.flip()).asInstanceOf[ByteBuffer]).toString()
            buffer.clear()
        }
        return result
    }
}

class Request extends Actor {
    def act(): Unit = {
        react {
            case socket: SocketChannel =>
                try {
		    var directory: String = Server.fileDir
                    var buffer: ByteBuffer = ByteBuffer.allocate(2048)
		    socket.write(ByteBuffer.wrap("Login Please:\f".getBytes("UTF-8")))
                    socket.read(buffer)
		    if(((buffer.flip()).asInstanceOf[ByteBuffer]).toString().compare("Username:\f") == 0) {
		        buffer.clear()
			val username = ((buffer.flip()).asInstanceOf[ByteBuffer]).asCharBuffer().toString()
			buffer.clear()
			if(((buffer.flip()).asInstanceOf[ByteBuffer]).asCharBuffer().toString().compare("Password:\f") == 0) {
			    buffer.clear()
			    val password = ((buffer.flip()).asInstanceOf[ByteBuffer]).toString()
		            for((user: NodeSeq) <- Server.userList \\ "user") {
			        if((user \\ "@name").text.compare(username) == 0 & (user \\ "password").text.compare(password) == 0) {
				    directory += "/" + username
				}
			    }
			    if(directory.compare(Server.fileDir) == 0) {
			        // Invalid username or password
				socket.write(ByteBuffer.wrap("Alert:\f".getBytes("UTF-8")))
				socket.write(ByteBuffer.wrap("Invalid username or password".getBytes("UTF-8")))
				socket.write(ByteBuffer.wrap("\f\f".getBytes("UTF-8")))
				return
			    }
			} else {
			    // Client error
			    socket.write(ByteBuffer.wrap("Alert:\f".getBytes("UTF-8")))
			    socket.write(ByteBuffer.wrap(("Unexpected command: " + ((buffer.flip()).asInstanceOf[ByteBuffer]).asCharBuffer().toString).getBytes("UTF-8")))
			    socket.write(ByteBuffer.wrap("\f\f".getBytes("UTF-8")))
			    return
			}
		    } else {
		        // Client error
			socket.write(ByteBuffer.wrap("Alert:\f".getBytes("UTF-8")))
			socket.write(ByteBuffer.wrap(("Unexpected command: " + ((buffer.flip()).asInstanceOf[ByteBuffer]).asCharBuffer().toString()).getBytes("UTF-8")))
			socket.write(ByteBuffer.wrap("\f\f".getBytes("UTF-8")))
			return
		    }
		    var dirContents: ArrayBuffer[String] = new ArrayBuffer[String](40)
		    val rootContents = listDir(directory)
		    for((file: String) <- rootContents) {
		        dirContents += "root"
		        dirContents += file
		    }
		    var location: String = null
		    var odd: Boolean = true
                    for((file: String) <- dirContents) {
		        if(odd) {
			    odd = false
			    location = file
			} else {
			    odd = true
		            if(isDirectory(file)) {
			        val subdirContents = listDir(file)
			        for((subdirFile: String) <- subdirContents) {
				    dirContents += file
			            dirContents += subdirFile
			        }
			        socket.write(ByteBuffer.wrap("Show Directory:\f".getBytes("UTF-8")))
			    } else {
			        socket.write(ByteBuffer.wrap("Show File:\f".getBytes("UTF-8")))
			    }
			    socket.write(ByteBuffer.wrap(location.getBytes("UTF-8")))
			    socket.write(ByteBuffer.wrap(file.getBytes("UTF-8")))
			    socket.write(ByteBuffer.wrap("\f\f".getBytes("UTF-8")))
			}
		    }
		    while(socket.isConnected()) {
                        socket.read(buffer)
                        val filename = ((buffer.flip()).asInstanceOf[ByteBuffer]).asCharBuffer.toString()
                        if(isDirectory(filename) == true) {
			    dirContents = dirContents.drop(dirContents.length)
			    location = Server.fileDir + "/" + filename + "/"
                            socket.write(ByteBuffer.wrap("Directory:\f".getBytes("UTF-8")))
			    socket.write(ByteBuffer.wrap(location.getBytes("UTF-8")))
			    socket.write(ByteBuffer.wrap("\f\f".getBytes("UTF-8")))
			    for((file: String) <- dirContents) {
		                if(odd) {
			            odd = false
			            location = file
			        } else {
			            odd = true
		                    if(isDirectory(file)) {
			                val subdirContents = listDir(file)
			                for((subdirFile: String) <- subdirContents) {
					    dirContents += file
			                    dirContents += subdirFile
			                }
			                socket.write(ByteBuffer.wrap("Directory:\f".getBytes("UTF-8")))
					socket.write(ByteBuffer.wrap(location.getBytes("UTF-8")))
					socket.write(ByteBuffer.wrap(file.getBytes("UTF-8")))
			            } else {
			                socket.write(ByteBuffer.wrap("File:\f".getBytes("UTF-8")))
					socket.write(ByteBuffer.wrap((location + file).getBytes("UTF-8")))
					var fchannel: FileChannel = new FileInputStream(file).getChannel()
					var filedata: String = ""
					buffer.clear()
					while(fchannel.read(buffer) != -1) {
					    filedata += ((buffer.flip()).asInstanceOf[ByteBuffer]).asCharBuffer().toString()
					    buffer.clear()
					}
					socket.write(ByteBuffer.wrap(filedata.getBytes("UTF-8")))
			            }
			            socket.write(ByteBuffer.wrap("\f\f".getBytes("UTF-8")))
			        }
			    }
                        } else {
                            socket.write(ByteBuffer.wrap("File:\f".getBytes("UTF-8")))
			    socket.write(ByteBuffer.wrap((location + filename).getBytes("UTF-8")))
			    var fchannel: FileChannel = new FileInputStream(filename).getChannel()
			    var filedata: String = ""
			    buffer.clear()
			    while(fchannel.read(buffer) != -1) {
			        filedata += ((buffer.flip()).asInstanceOf[ByteBuffer]).asCharBuffer().toString()
			        buffer.clear()
			    }
			    socket.write(ByteBuffer.wrap(filedata.getBytes("UTF-8")))
			    socket.write(ByteBuffer.wrap("\f\f".getBytes("UTF-8")))
                        }
			buffer.clear()
                    }
                } catch {
                    case ex: IOException =>
                        ex.printStackTrace()
			socket.write(ByteBuffer.wrap("Alert:\f".getBytes("UTF-8")))
			socket.write(ByteBuffer.wrap("Error opening file".getBytes("UTF-8")))
			socket.write(ByteBuffer.wrap("\f\f".getBytes("UTF-8")))
                }
        }
    }
    
    private def listDir(path: String): Array[String] = {
        val files = new ArrayBuffer[String](20)
        var rootDir: Iterable[Path] = Paths.get(path).asScala
        for(subDir <- rootDir) {
            files += subDir.toString
            try {
                var stream: DirectoryStream[Path] = Files.newDirectoryStream(subDir)
                for(file <- stream.asScala) {
                    files += file.toString
                }
            }
        }
        return files.toArray
    }
    
    private def isDirectory(filename: String): Boolean = {
        try {
            var file: File = new File(filename)
            if(file.isDirectory) {
                return true
            } else {
                return false
            }
        } catch {
            case ex: IOException =>
                ex.printStackTrace()
                return false
        }
    }
}