package com.khjxiaogu.autoupdate.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Variables {

	public static boolean guidisplayed=false;
	//have to do this hacks to share variables between different classloaders
	public static boolean shouldUpdate() {
		return Boolean.parseBoolean(System.getProperty("tssshouldupdate","true"));
	}
	public static void setShouldUpdate(boolean shouldUpdate) {
		System.setProperty("tssshouldupdate",String.valueOf(shouldUpdate));
	}
	public static boolean isUpdateSuccess() {
		return Boolean.parseBoolean(System.getProperty("tssupdatesuccess","false"));
	}
	public static void setUpdateSuccess(boolean updateSuccess) {
		System.setProperty("tssupdatesuccess",String.valueOf(updateSuccess));
	}
	public static Path getBackupPath() {
		return Paths.get(System.getProperty("tssbackuppath"));
	}
	public static void setBackupPath(Path backup) {
		System.setProperty("tssbackuppath",String.valueOf(backup));
	}
	public static boolean shouldRestart() {
		return Boolean.parseBoolean(System.getProperty("tssrestart","false"));
	}
	public static void restart() {
		System.setProperty("tssrestart","true");
	}
}
