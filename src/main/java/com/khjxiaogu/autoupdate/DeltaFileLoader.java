package com.khjxiaogu.autoupdate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.khjxiaogu.autoupdate.utils.FileUtil;
import com.khjxiaogu.autoupdate.utils.ZipMaker;
public class DeltaFileLoader {
	Scanner delta;
	ZipInputStream pack;
	Path fd;
	ZipMaker bkf;
	Map<String,byte[]> f=new HashMap<>();
	private static final Logger LOGGER = LogManager.getLogger("TWR Startup Script Unpack");
	public DeltaFileLoader(InputStream delta,InputStream pack,Path outdir,ZipMaker bkf) throws ZipException, IOException {
		this.delta=new Scanner(delta);
		this.pack=new ZipInputStream(pack);
		this.fd=outdir;
		this.bkf=bkf;
	}
	public void run() throws IOException{
		try {
			ZipEntry zi;
			while((zi=pack.getNextEntry())!=null) {
				if(!zi.isDirectory())
				f.put(zi.getName(),FileUtil.readAll(pack));
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		while(delta.hasNext()) {
			String dl=delta.nextLine();
			int cpos=dl.indexOf('#');
			if(cpos!=-1)
				dl=dl.substring(0, cpos).trim();
			if(dl.isEmpty())continue;
			char op=dl.charAt(0);
			String path=dl.substring(1);
			if(op=='-') {
				Path todel=fd.resolve(path).normalize();
				if(!todel.startsWith(fd)) {//do security check to prevent malicious modification
					LOGGER.warn("ILLEGAL PATH ACCESS OUTSIDE /.minecraft! Consider this mod has been modified maliciously.");
					continue;
				}
				delete(new File(todel.toString()));
				LOGGER.info("Deleted "+path);
			}else if(op=='+') {
				if(path.equals("*")) {
					LOGGER.info("Unpacking files...");
					for(Entry<String, byte[]> i:f.entrySet()) {
						overwrite(i.getKey(),i.getValue());
						LOGGER.info("Added "+i.getKey());
					}
					continue;
				}
				Path toadd=fd.resolve(path).normalize();
				if(!toadd.startsWith(fd)) {//do security check to prevent malicious modification
					LOGGER.warn("ILLEGAL PATH ACCESS OUTSIDE /.minecraft! Consider this mod has been modified maliciously.");
					continue;
				}
				overwrite(path,f.get(path));
				LOGGER.info("Added "+path);
			}
		}
	}
	void overwrite(String rfn,byte[] bs) throws IOException {
		if(bs==null)return;
		File towrite=fd.resolve(rfn).toFile();
		bkf.add(towrite);
		towrite.getParentFile().mkdirs();
		try(FileOutputStream fos=new FileOutputStream(towrite)){
			fos.write(bs);
		}
	}
	void delete(File f) throws IOException {
		if(f.isDirectory()) {
		    File[] fs = f.listFiles();
		    if (fs != null) {
		        for (File file : fs) {
		        	delete(file);
		        }
		    }
		}else {
			bkf.add(f);
		}
	    f.delete();
	}
}
