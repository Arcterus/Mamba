package mamba

import java.nio.channels._
import java.nio.file._
import java.net._
import scala.actors.Actor._

object Server {
    var fileDir: String = ""
    
    def main(args: Array[String]) = {
        try {
            this.loadConf()
            val server = ServerSocketChannel.open()
            server.configureBlocking(false)
            server.socket().bind(InetSocketAddress(457))
            SocketChannel socket = server.accept()
            if(socket != Null) {
                // Request
                (new Request).start
            }
        } catch {
            case ex: IOException =>
                ex.printStackTrace()
        }
    }
    
    def loadConf(): Void = {
        val data = this.readAll(FileInputStream("/etc/mamba.conf").getChannel())
        var currentWord: String = ""
        var ignore: Boolean = false
        var inQuote: Boolean = false
        var prevChar: Character = '\0'
        var operation: Integer = 0
        def alphaFunc(ch: Character): Void = {
            if(ignore == false) {
                currentWord += toString(ch)
            }
        }
        for(ch <- data) {
            ch match {
                case ch if 'a' until 'z' contains ch =>
                    alphaFunc(ch)
                case ch if 'A' until 'Z' contains ch =>
                    alphaFunc(ch)
                case ch if '0' until '9' contains ch | '_' =>
                    alphaFunc(ch)
                case '#' =>
                    if(inQuote == true) {
                        currentWord += "#"
                    } else if(ignore == false) {
                        ignore = true
                    }
                case ' ' | '\t' =>
                    if(inQuote == true) {
                        currentWord += toString(ch)
                    } else if(operation != 0) {
                        operation match {
                            case 1 =>
                                fileDir = currentWord
                            case 2 =>
                                // For ze future
                        }
                    } else if(currentWord == "fileDir") {
                        operation = 1
                    }
                case '\n' =>
                    if(ignore == true) {
                        ignore = false
                    } else if(operation != 0) {
                        opeartion = 0
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
                default =>
                    // Do nothing for now
            }
            prevChar = ch
        }
    }
    
    private def readAll(file: FileChannel): String = {
        var result: String = ""
        var buffer: ByteBuffer = ByteBuffer.allocate(1024)
        while(buffer.read(file) != -1) {
            result += ((ByteBuffer) (buffer.flip())).asCharBuffer().get(0)
            buffer.clear()
        }
        return result
    }
}

class Request extends Actor {
    def handle(): Boolean = {
        
        return true
    }
    
    private def listDir(path: Path): Array[String] = {
        var files: Array[String] = Null
        Iterable<Path> rootDir = path
        for(subDir <- rootDir) {
            files.append(subDir.getFileName())
            try {
                var stream/*: DirectoryStream<Path>*/ = Files.newDirectoryStream(subDir)
                for(file <- stream) {
                    files.append(file.getFileName())
                }
            }
        }
    }
}