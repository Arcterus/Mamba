package mamba;

import java.nio.channels._
import java.net._
import scala.actors.Actor._

object Server {
    var fileDir: String = ""
    
    def main(args: String[]) {
        try {
            this.loadConf()
            val server = ServerSocketChannel.open()
            server.configureBlocking(false)
            server.socket().bind(InetSocketAddress(457))
            SocketChannel socket = server.accept()
            if(socket == Null) {
                // No request
            } else {
                // Request
                (new Request).start
            }
        } catch(IOException ex) {
            ex.printStackTrace()
        }
    }
    
    def loadConf(): Void {
        val data = this.readAll(FileInputStream("/etc/mamba.conf").getChannel())
        var currentWord: String = ""
        var ignore: Boolean = false
        var operation: Integer = 0
        data.foreach(ch: Character =>
            ch match {
                case 'a'..'z' | 'A'..'Z' | '0'..'9' | '_' =>
                    if(ignore == false) {
                        currentWord += toString(ch)
                    }
                case '#' =>
                    if(ignore == false) {
                        ignore = true
                    }
                case ' ' | '\t' =>
                    if(operation != 0) {
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
                default =>
                    // Do nothing for now
            }
    }
    
    private def readAll(file: FileChannel): String {
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
    def handle(): Boolean {
        return true;
    }
}