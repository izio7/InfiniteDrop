package com.green.back;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.HashMap;
import java.util.List;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import almonds.FindCallback;
import almonds.ParseException;
import almonds.ParseObject;

import com.dropbox.core.*;
import com.dropbox.core.DbxDelta.Entry;

public class RemoteFileManager implements Runnable {
	private DatabaseConnection databaseConnection;
	private HashMap<String, String> cursors;
	private ArrayBlockingQueue<Entry<DbxEntry>> eventQueue;
	
	private static final String SAVE_FILE = LocalFileManager.BASE_DIR + ".cursors";

	public RemoteFileManager() {
		cursors = new HashMap<String, String>();
		databaseConnection = new DatabaseConnection();
		eventQueue = new ArrayBlockingQueue<Entry<DbxEntry>>(200);
		
		readState();
	}

	public void run() {
		for (;;) {
			List<DbxClient> clients = databaseConnection.getDbxClients();
			for (DbxClient client : clients) {
				try {
					DbxDelta<DbxEntry> delta = client.getDelta(cursors.get(client.getAccessToken()));
					 cursors.put(client.getAccessToken(), delta.cursor);
					eventQueue.addAll(delta.entries);
				} catch (DbxException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public Entry<DbxEntry> takeDbxEvent() throws InterruptedException {
		return eventQueue.take();
	}
	
	public void readState() {
		List<DbxClient> clients = databaseConnection.getDbxClients();
		HashMap<String, String> tempCursors = new HashMap<String, String>();
		File saveFile = new File(RemoteFileManager.SAVE_FILE);
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(saveFile));
			String line;
			while ((line = br.readLine()) != null) {
			   String left = line.substring(0, line.indexOf(":"));
			   String right = line.substring(line.indexOf(":") + 1);
			   tempCursors.put(left, right);
			}
			
			br.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for(DbxClient client: clients) {
			if(tempCursors.containsKey(client.getAccessToken())) {
				cursors.put(client.getAccessToken(), tempCursors.get(client.getAccessToken()));
			}
		}
		
	}
	
	public void saveState() {
		File saveFile = new File(RemoteFileManager.SAVE_FILE);
		try {
			FileWriter f = new FileWriter(saveFile);
			for(Map.Entry<String, String> e: cursors.entrySet()) {
				f.write(e.getKey() + ":" + e.getValue() + "\n");
			}
			f.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	public void saveFile(Path path) {
		File inputFile = path.toFile();
		DbxClient client = databaseConnection.getDbxClient(getLargestAccount());
		String hash = CombinedFileManager.getHash(LocalFileManager
				.getRelativePath(path.toString()));
		FileInputStream inputStream = null;
		try {
			inputStream = new FileInputStream(inputFile);
			DbxEntry.File uploadedFile = client.uploadFile("/" + hash,
					DbxWriteMode.force(), inputFile.length(), inputStream);
			System.out.println("Uploaded: " + uploadedFile.toString());
		} catch (DbxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void saveFolder(Path path) {
		DbxClient client = databaseConnection.getDbxClient(getLargestAccount());
		String hash = CombinedFileManager.getHash(LocalFileManager
				.getRelativePath(path.toString()));
		try {
			client.createFolder("/" + hash);
			System.out.println("Created Folder");
		} catch (DbxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void deleteFile(String path, String clientToken) {
		DbxClient client = databaseConnection.getDbxClient(clientToken);
		try {
			client.delete("/" + path);
		} catch (DbxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public DbxEntry.File downloadFile(String hash) {
		ParseObject record = databaseConnection.getFileRecordFromHash(hash);
		String localName = record.getString("file");
		DbxClient client = databaseConnection.getDbxClient(record.getString("dbxAccount"));
		FileOutputStream outputStream;

		try {
			outputStream = new FileOutputStream(LocalFileManager.BASE_DIR + "/" + localName);
		    DbxEntry.File downloadedFile = client.getFile("/magnum-opus.txt", null, outputStream);
		    System.out.println("Metadata: " + downloadedFile.toString());
		    outputStream.close();
		    return downloadedFile;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DbxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public String getLargestAccount() {
		long max = Long.MIN_VALUE;
		String dbxAccnt = null;
		for (DbxClient client : databaseConnection.getDbxClients()) {
			long curr = Long.MIN_VALUE;
			try {
				curr = client.getAccountInfo().quota.total
						- client.getAccountInfo().quota.normal;
			} catch (DbxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (curr > max) {
				max = curr;
				dbxAccnt = client.getAccessToken();
			}
		}
		return dbxAccnt;
	}
}
