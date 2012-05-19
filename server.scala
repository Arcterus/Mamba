package mamba

import java.nio.channels._
import java.nio.file._
import java.nio.ByteBuffer
import java.io._
import java.net._
import scala.actors._
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._

object Server {
    var fileDir: String = ""
    
    def main(args: Array[String]) = {
        try {
            this.loadConf()
            val server = ServerSocketChannel.open()
            server.configureBlocking(false)
            server.socket().bind(new InetSocketAddress(457))
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
    
    def loadConf(): Void = {
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
            result += ((buffer.flip()).asInstanceOf[ByteBuffer]).asCharBuffer().get(0)
            buffer.clear()
        }
        return result
    }
}

class Request extends Actor {
    def act() = {
        react {
            case socket: SocketChannel =>
                try {
                    var buffer: ByteBuffer = ByteBuffer.allocate(2048)
                    socket.read(buffer)
                    val directory = Server.fileDir + ((buffer.flip()).asInstanceOf[ByteBuffer]).asCharBuffer().get(0) + "/"
                    buffer.clear()
                    val filelist = listDir(directory)
                    for(filename <- filelist) {
                        socket.write(ByteBuffer.wrap(filename.getBytes("UTF-8")))
                    }
                    socket.read(buffer)
                    val filename = ((buffer.flip()).asInstanceOf[ByteBuffer]).asCharBuffer().get(0)
                    if(isDirectory(directory + filename) == true) {
                        val newfilelist = listDir(directory + filename)
                        socket.write("directory: \n")
                        for(newfiles <- newfilelist) {
                            socket.write(ByteBuffer.wrap(filename.getBytes("UTF-8")))
                        }
                    } else {
                        var file: FileChannel = new FileInputStream(filename).getChannel()
                        socket.write("file: \n")
                        while(file.read(buffer) != -1) {
                            socket.write(((buffer.flip()).asInstanceOf[ByteBuffer]).asCharBuffer().get(0))
                            buffer.clear()
                        }
                    }
                    act()
                } catch {
                    case ex: IOException =>
                        ex.printStackTrace()
                        var buffer: ByteBuffer = ByteBuffer.allocate(2048)
                        for((ch: Char) <- "alert: Error opening file") {
                            buffer.putChar(ch)
                        }
                        socket.write(buffer)
                }
        }
    }
    
    private def listDir(path: String): Array[String] = {
        val files = new ArrayBuffer[String](20)
        var rootDir: Iterable[Path] = Paths.get(path).asScala
        for(subDir <- rootDir) {
            files += subDir.toAbsolutePath().toString
            try {
                var stream: DirectoryStream[Path] = Files.newDirectoryStream(subDir)
                for(file <- stream.asScala) {
                    files += file.toAbsolutePath().toString
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
