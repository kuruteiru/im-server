package utb.fai;

import java.util.*;

public class ActiveHandlers {
	private final Set<SocketHandler> activeHandlersSet = new HashSet<>();
	private final Map<String, SocketHandler> usernameToHandlerMap = new HashMap<>();
	private final Map<String, Set<SocketHandler>> groups = new HashMap<>();

	// synchronized void sendMessageToAll(SocketHandler sender, String message) {
	// 	for (SocketHandler handler : activeHandlersSet) {
	// 		if (handler != sender) {
	// 			handler.messages.offer(message);
	// 		}
	// 	}
	// }

	synchronized void sendMessageToGroupExcept(String group, SocketHandler sender, String message) {
		Set<SocketHandler> members = groups.get(group);
		if (members != null) {
			for (SocketHandler handler : members) {
				if (handler != sender) {
					handler.messages.offer(message);
				}
			}
		}
	}

	synchronized boolean add(SocketHandler handler) {
		return activeHandlersSet.add(handler);
	}

	synchronized boolean remove(SocketHandler handler) {
		activeHandlersSet.remove(handler);
		if (handler.username != null) {
			usernameToHandlerMap.remove(handler.username);
		}
		for (Set<SocketHandler> groupSet : groups.values()) {
			groupSet.remove(handler);
		}
		cleanupGroups();
		return true;
	}

	synchronized boolean isUsernameTaken(String username) {
		return usernameToHandlerMap.containsKey(username);
	}

	synchronized SocketHandler getHandlerByUsername(String username) {
		return usernameToHandlerMap.get(username);
	}

	synchronized void registerUsername(String username, SocketHandler handler) {
		usernameToHandlerMap.put(username, handler);
	}

	synchronized void deregisterUsername(String username) {
		usernameToHandlerMap.remove(username);
	}

	synchronized void joinGroup(SocketHandler handler, String groupName) {
		groups.computeIfAbsent(groupName, k -> new HashSet<>()).add(handler);
	}

	synchronized void leaveGroup(SocketHandler handler, String groupName) {
		Set<SocketHandler> set = groups.get(groupName);
		if (set != null) {
			set.remove(handler);
			if (set.isEmpty()) {
				groups.remove(groupName);
			}
		}
	}

	synchronized Set<String> getGroupsFor(SocketHandler h) {
		Set<String> result = new HashSet<>();
		for (Map.Entry<String, Set<SocketHandler>> entry : groups.entrySet()) {
			if (entry.getValue().contains(h)) {
				result.add(entry.getKey());
			}
		}
		return result;
	}

	private void cleanupGroups() {
		groups.entrySet().removeIf(entry -> entry.getValue().isEmpty());
	}

	// synchronized Set<String> listGroups() {
	// 	Set<String> nonEmptyGroups = new TreeSet<>();
	// 	for (Map.Entry<String, Set<SocketHandler>> entry : groups.entrySet()) {
	// 		if (!entry.getValue().isEmpty()) {
	// 			nonEmptyGroups.add(entry.getKey());
	// 		}
	// 	}
	// 	return nonEmptyGroups;
	// }

	// synchronized Set<SocketHandler> getGroupMembers(String groupName) {
	// 	return groups.getOrDefault(groupName, Collections.emptySet());
	// }
}
