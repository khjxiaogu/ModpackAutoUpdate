package com.khjxiaogu.autoupdate.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

public class ZipMaker implements AutoCloseable{
	Path fd;
	ZipOutputStream bkf;
	Set<String> entries=new HashSet<>();
	public ZipMaker(File output,Path indir) throws ZipException, IOException {
		fd=indir.toAbsolutePath();
		bkf=new ZipOutputStream(new FileOutputStream(output));
	}
	public ZipMaker(OutputStream output,Path indir) throws ZipException, IOException {
		fd=indir.toAbsolutePath();
		bkf=new ZipOutputStream(output);
	}
	public void close() throws IOException {
		bkf.close();
	}
	public void add(File f,Predicate<File> p) throws IOException {
		if(!f.exists())return;
		if(f.isDirectory()&&p.test(f)) {
		    File[] fs = f.listFiles();
		    if (fs != null) {
		        for (File file : fs) {
		        	add(file,p);
		        }
		    }
		}else {
			if(p.test(f))
				addFile(f);
		}
	}
	public void add(File f) throws IOException {
		if(!f.exists())return;
		if(f.isDirectory()) {
		    File[] fs = f.listFiles();
		    if (fs != null) {
		        for (File file : fs) {
		        	add(file);
		        }
		    }
		}else {
			addFile(f);
		}
	}
	public void add(File f,String basename) throws IOException {
		basename+="/";
		if(f.isDirectory()) {
		    File[] fs = f.listFiles();
		    if (fs != null) {
		        for (File file : fs) {
		        	add(file,basename+f.getName());
		        }
		    }
		}else {
			addFile(f,basename+f.getName());
		}
	}
	public void addData(String fn,byte[] f) throws IOException {
		String ff=fn.replace("\\","/");
		if(entries.add(ff.toLowerCase())) {
			bkf.putNextEntry(new ZipEntry(ff));
			bkf.write(f);
			bkf.closeEntry();
		}
	}
	// String normalize
	public void addFile(File f,String fn) throws IOException {
		if(f.exists()) {
			String ff=fn.replace("\\","/");
			if(entries.add(ff.toLowerCase())) {
				bkf.putNextEntry(new ZipEntry(ff));
				FileUtil.transfer(f, bkf);
				bkf.closeEntry();
			}
		}
	}
	// String normalize
	public void addFile(File f) throws IOException {
		if(f.exists()) {
			
			String rp=fd.relativize(f.toPath().toAbsolutePath()).toString().replace("\\","/");
			
			if(entries.add(rp.toLowerCase())) {
				ZipEntry ze=new ZipEntry(rp);
				bkf.putNextEntry(ze);
				FileUtil.transfer(f, bkf);
				bkf.closeEntry();
			}
		}
	}
}
