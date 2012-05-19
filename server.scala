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
            var socket: SocketChannel = server.accept()
            if(socket != null) {
                // Request
                (new Request).start()
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
                    // Do nothing for now
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
    }
    
    private def listDir(path: Path): Array[String] = {
        val files = new ArrayBuffer(20)
        var rootDir: Iterable[Path] = path.asScala
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
}
