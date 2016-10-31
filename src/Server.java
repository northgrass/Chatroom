import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class Server {
	public static void main(String args[]) throws Exception {

		try {
		int port = 12345;
		ServerSocket server = new ServerSocket(port, 70);
		System.out.println(server);
		Map<String, Socket> onlineClients = new ConcurrentHashMap<String, Socket>();
		Map<Socket, ForwardMessage> threadRelation = new ConcurrentHashMap<Socket, ForwardMessage>();
		Map<Socket, LinkedBlockingQueue<String>> forwardClients = new ConcurrentHashMap<Socket, LinkedBlockingQueue<String>>();
			while (true) {
				Socket socket = server.accept();
				LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<String>();
				forwardClients.put(socket, queue);
				ForwardMessage f = new ForwardMessage(socket, forwardClients);
				threadRelation.put(socket, f);
				Task t = new Task(socket, onlineClients, forwardClients,threadRelation);
				t.start();
				f.start();

			}
		} catch (BindException e) {
			System.out.println("Address already in use!");
		} 
	}
}

class Task extends Thread {

	private Socket client;
	private BufferedReader br;
	private PrintWriter pw;
	private String userName;
	private Map<String, Socket> onlineClients;
	private Map<Socket, LinkedBlockingQueue<String>> forwardClients;
	private Map<Socket, ForwardMessage> threadRelation;
	private Boolean loginSuccessed = true;
	private Boolean flag = true;

	public Task(Socket client, Map<String, Socket> onlineClients,
			Map<Socket, LinkedBlockingQueue<String>> forwardClients,Map<Socket, ForwardMessage> threadRelation) throws Exception {
		super();
		this.client = client;
		this.onlineClients = onlineClients;
		this.forwardClients = forwardClients;
		this.threadRelation = threadRelation;
	}

	public void run() {
		try {
			br = new BufferedReader(new InputStreamReader(
					this.client.getInputStream()));
			pw = new PrintWriter(client.getOutputStream(), true);
			forwardClients.get(client).offer("please login");
			String login = "";
			login = br.readLine();
			while (flag && login != null) {
				login(login);
				if (flag) {
					login = br.readLine();
				}
			}
		} catch (IOException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
		String content;
		try {
			while (loginSuccessed && (userName != null)
					&& (content = br.readLine()) != null) {
				if (content.equals("/quit")) {// 用户正常登陆后使用命令/quit退出
					quit();
					loginSuccessed = false;
					break;
				} else {
					dealWithContact(content);
				}
			}
		} catch (IOException e) {
			loginSuccessed = false;
			remindBroadcast(userName + " has quit!");// 用户登录后强制退出
			onlineClients.remove(userName);
		}
	}


	// 广播消息
	public void broadcast(String content) {
		remindBroadcast(userName + "说:" + content);
		forwardClients.get(client).offer("你说:" + content);
	}

	// 私聊
	public void privateChat(String content) {
		try {
			String[] str = content.split(" ");
			String receiveName = null;
			String message = null;
			if (str.length == 2) {
				receiveName = str[1];
				if (onlineClients.containsKey(receiveName)) {
					forwardClients.get(client).offer("发送内容不能为空！");
				} else {
					forwardClients.get(client).offer("Invalid command!");
				}

			} else if (str.length > 2) {
				receiveName = str[1];
				message = content.replace("/to " + receiveName, userName
						+ "对你说:");
				Socket toClient = (Socket) onlineClients.get(receiveName);
				if ((toClient != null) && (toClient != client)) {
					forwardClients.get(toClient).offer(message);
					String messageToMe = message.replace(userName + "对你说:",
							"你对" + receiveName + "说:");
					forwardClients.get(client).offer(messageToMe);
				}
				if (toClient == client) {
					forwardClients.get(client).offer("停止对自己讲话！");
				}
				if (toClient == null) {
					forwardClients.get(client).offer("该用户暂时不在线！");
				}
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	// 查看在线用户及人数
	public void onlineUser() {
		java.util.Iterator<Entry<String, Socket>> iter = onlineClients
				.entrySet().iterator();
		int num = 0;
		try {
			while (iter.hasNext()) {
				Entry entry = (Entry) iter.next();
				Object key = entry.getKey();
				System.out.println(key);
				forwardClients.get(client).offer((String) key);
				num++;
			}
			forwardClients.get(client).offer("Total online user:" + num);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	// 发送预设命令
	public void preOrder(String content) {
		String[] str = content.split(" ");
		String toWho = null;
		String text = null;
		if (content.equals("//hi")) {
			toWho = "向大家";
			text = "打招呼：“Hi，大家好！我来咯~”";
		} else if ((content.startsWith("//hi")) && str.length == 2) {
			if (str[1].equals(userName)) {
				forwardClients.get(client).offer("不要同自己打招呼！");
				return;
			} else if (onlineClients.containsKey(str[1])) {
				toWho = "向" + str[1];
				text = "打招呼：“Hi，你好啊~”";
			} else {
				forwardClients.get(client).offer("你要打招呼的用户暂时不在线！");
				return;
			}
		} else if (content.equals("//smile")) {
			toWho = "";
			text = "脸上泛起无邪的笑容";
		} else {
			forwardClients.get(client).offer("不存在的预设消息！");
			return;
		}
		forwardClients.get(client).offer(userName + toWho + text);
		remindBroadcast(userName + toWho + text);
	}

	// 群发消息提示（除了自己）
	public void remindBroadcast(String content) {
		try {
			Collection<Socket> values = onlineClients.values();
			for (Socket value : values) {
				if (value != client) {
					forwardClients.get(value).offer(content);
				}
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	// 退出
	public void quit() {
		remindBroadcast(userName + " has quit!");
		onlineClients.remove(userName);
		try {
			br.close();
			pw.close();
			client.close();
			threadRelation.get(client).interrupt();
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	// 登陆
	public void login(String login) {
		String[] str = login.split(" ");
		if ((login.startsWith("/login ")) && str.length > 1) {
			userName = str[1];
			if (onlineClients.containsKey(userName)) {
				forwardClients.get(client).offer(
						"Name exist, please choose anthoer name!");
			} else {
				forwardClients.get(client).offer("You have logined!");
				onlineClients.put(userName, client);
				remindBroadcast(userName + " has logined!");
				flag = false;
			}
		} else if (login.equals("/quit")) { // 用户未登录时直接命令/quit退出
			loginSuccessed = false;
			try {
				br.close();
				pw.close();
				client.close();
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
			flag = false;
		} else {
			forwardClients.get(client).offer("Invalid command!");
		}
	}

	// 处理消息
	public void dealWithContact(String content) {
		if (!(content.startsWith("/"))) {
			broadcast(content);
		} else if (content.startsWith("/to ")) {
			privateChat(content);
		} else if (content.equals("/who")) {
			onlineUser();
		} else if (content.startsWith("//")) {
			preOrder(content);
		} else if (content.equals("/quit")) {
			quit();
		} else if (content.startsWith("/login ")) {
			forwardClients.get(client).offer("你已经登录，请不要重复登录！");
		} else {
			forwardClients.get(client).offer("Invalid command!");
		}
	}
}

class ForwardMessage extends Thread {

	private Socket socket;
	private Map<Socket, LinkedBlockingQueue<String>> forwardClients;

	public ForwardMessage(Socket socket,
			Map<Socket, LinkedBlockingQueue<String>> forwardClients) {
		super();
		this.socket = socket;
		this.forwardClients = forwardClients;
	}

	public void run() {
		LinkedBlockingQueue<String> quene = (LinkedBlockingQueue<String>) forwardClients.get(socket);
		while (true) {
			try {
				String info = quene.take().toString();
				
				PrintWriter pw;
					pw = new PrintWriter(socket.getOutputStream(), true);
					pw.println(info);
				} catch (Exception e) {
					e.printStackTrace();
				}
			
		}
	}

}
