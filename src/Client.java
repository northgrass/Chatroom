import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {

	public static void main(String args[]) throws Exception {
		String host = "127.0.0.1"; // 要连接的服务端IP地址
		int port = 12345; // 要连接的服务端对应的监听端口
		Socket client = new Socket(host, port);
		System.out.println(client);

		new SendMessage(client).start();
		new ReceiveMessage(client).start();

	}
}

class SendMessage extends Thread {
	private Socket client1;
	private BufferedReader br1;

	public SendMessage(Socket client1) throws Exception {
		super();
		this.client1 = client1;
		this.br1 = br1;
	}

	@Override
	public void run() {
		InputStreamReader inRead = new InputStreamReader(System.in);
		BufferedReader br1 = new BufferedReader(inRead);
		try {
			String line;
			line = br1.readLine();
			while (line != null) {
				PrintWriter pw = new PrintWriter(client1.getOutputStream(),
						true);
				pw.println(line);
				if (line.equals("/quit")) {
					break;
				} else {
					line = br1.readLine();
				}
			}
			br1.close();
			client1.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}

class ReceiveMessage extends Thread {
	private Socket client2;

	public ReceiveMessage(Socket client2) throws Exception {
		super();
		this.client2 = client2;
	}

	@Override
	public void run() {
		try {
			BufferedReader br2 = new BufferedReader(new InputStreamReader(
					client2.getInputStream()));
			while (!(client2.isClosed())) {
				String reply = null;
				if ((reply = br2.readLine()) != null) {
					System.out.println(reply);
				}
			}
			br2.close();
			client2.close();
		} catch (IOException e) {
			if (e.getMessage().equals("Connection reset")) {
				System.out.println("服务器异常中断！");
			} else if (e.getMessage().equals("Socket closed")) {
				System.out.println("你已退出！");
			} else {
				System.out.println(e.getMessage());
			}

		}

	}
}