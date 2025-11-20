package utb.fai;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.Set;

public class SocketHandler {
	Socket mySocket;
	String clientID;
	String username;
	ActiveHandlers activeHandlers;
	ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<>(20);

	CountDownLatch startSignal = new CountDownLatch(2);
	OutputHandler outputHandler = new OutputHandler();
	InputHandler inputHandler = new InputHandler();
	volatile boolean inputFinished = false;
	String group = null;

	public SocketHandler(Socket mySocket, ActiveHandlers activeHandlers) {
		this.mySocket = mySocket;
		clientID = mySocket.getInetAddress().toString() + ":" + mySocket.getPort();
		this.activeHandlers = activeHandlers;
		this.username = null;
	}

	class OutputHandler implements Runnable {
		public void run() {
			try (OutputStreamWriter writer = new OutputStreamWriter(mySocket.getOutputStream(), "UTF-8")) {
				startSignal.countDown();
				startSignal.await();
				writer.write("\nIM Server: Welcome! Please set your name using #setMyName <your_name>.\n");
				writer.flush();
				while (!inputFinished) {
					String message = messages.take();
					writer.write(message + "\r\n");
					writer.flush();
				}
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	class InputHandler implements Runnable {
		public void run() {
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(mySocket.getInputStream(), "UTF-8"))) {
				startSignal.countDown();
				startSignal.await();
				activeHandlers.add(SocketHandler.this);
				group = "public";
				activeHandlers.joinGroup(SocketHandler.this, group);

				String request;
				while ((request = reader.readLine()) != null) {
					if (request.trim().isEmpty()) {
						continue;
					}
					handleRequest(request.trim());
				}
				inputFinished = true;
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			} finally {
				if (group != null) {
					activeHandlers.leaveGroup(SocketHandler.this, group);
				}
				activeHandlers.remove(SocketHandler.this);
			}
		}

		private void handleRequest(String request) {
			if (username == null && request.startsWith("#setMyName ")) {
				setName(request.substring(11).trim());
				return;
			}
			if (username == null && !request.startsWith("#")) {
				setName(request.trim());
				return;
			}
			if (username == null) {
				messages.offer("IM Server: Please set your name using #setMyName <your_name> to send messages.");
				return;
			}
			if (request.startsWith("#setMyName ")) {
				changeName(request.substring(11).trim());
			} else if (request.startsWith("#sendPrivate ")) {
				handlePrivateMessage(request);
			} else if (request.startsWith("#join ")) {
				String newGroup = request.substring(6).trim();
				if (!newGroup.isEmpty()) {
					joinGroupRequest(newGroup);
				}
			} else if (request.equals("#groups")) {
				Set<String> myGroups = activeHandlers.getGroupsFor(SocketHandler.this);
				if (myGroups.isEmpty()) {
					messages.offer("IM Server: You are not in any group.");
				} else {
					messages.offer("IM Server: Active groups: " + String.join(", ", myGroups));
				}
			} else if (request.startsWith("#leave ")) {
				String group = request.substring(7).trim();
				if (!group.isEmpty()) {
					leaveGroupRequest(group);
				}
			} else {
				broadcastMessage(request);
			}
		}

		private void setName(String newName) {
			if (newName.contains(" ") || newName.isEmpty() || newName.startsWith("#")) {
				messages.offer("IM Server: Invalid name. Choose a name without spaces and not starting with '#'.");
				return;
			}
			synchronized (activeHandlers) {
				if (activeHandlers.isUsernameTaken(newName)) {
					messages.offer("IM Server: Username already taken. Please try another.");
				} else {
					username = newName;
					activeHandlers.registerUsername(username, SocketHandler.this);
					messages.offer("IM Server: Your username has been set to [" + username + "].");
					if (group != null) {
						activeHandlers.sendMessageToGroupExcept(group, SocketHandler.this,
								"IM Server: [" + username + "] has joined the chat!");
					}
				}
			}
		}

		private void changeName(String newName) {
			if (newName.contains(" ") || newName.isEmpty() || newName.startsWith("#")) {
				messages.offer("IM Server: Invalid name. Choose a name without spaces and not starting with '#'.");
				return;
			}

			synchronized (activeHandlers) {
				if (activeHandlers.isUsernameTaken(newName)) {
					messages.offer("IM Server: Username already taken. Please try another.");
				} else {
					activeHandlers.deregisterUsername(username);
					username = newName;
					activeHandlers.registerUsername(username, SocketHandler.this);
					messages.offer("IM Server: Your username has been set to [" + username + "].");
					if (group != null) {
						activeHandlers.sendMessageToGroupExcept(group, SocketHandler.this,
								"IM Server: [" + username + "] has joined the chat!");
					}
				}
			}
		}

		private void handlePrivateMessage(String request) {
			String[] parts = request.split(" ", 3);
			if (parts.length < 3) {
				messages.offer("IM Server: Invalid private message format. Use #sendPrivate <username> <message>.");
				return;
			}
			String targetUsername = parts[1];
			String privateMessage = parts[2];
			synchronized (activeHandlers) {
				SocketHandler target = activeHandlers.getHandlerByUsername(targetUsername);
				if (target != null) {
					target.messages.offer("[" + username + "] >> " + privateMessage);
				} else {
					messages.offer("IM Server: User [" + targetUsername + "] not found.");
				}
			}
		}

		private void joinGroupRequest(String newGroup) {
			if (group != null) {
				activeHandlers.leaveGroup(SocketHandler.this, group);
			}
			group = newGroup.replace(' ', '-');
			activeHandlers.joinGroup(SocketHandler.this, group);
			messages.offer("IM Server: You joined group [" + group + "].");
		}

		private void leaveGroupRequest(String levGroup) {
			if (group == null || !group.equals(levGroup)) {
				messages.offer("IM Server: You are not in group [" + levGroup + "].");
				return;
			}
			activeHandlers.leaveGroup(SocketHandler.this, group);
			messages.offer("IM Server: You left group [" + levGroup + "].");
			group = null;
		}

		private void broadcastMessage(String msg) {
			if (group != null) {
				activeHandlers.sendMessageToGroupExcept(group, SocketHandler.this, "[" + username + "] >> " + msg);
			}
		}
	}
}
