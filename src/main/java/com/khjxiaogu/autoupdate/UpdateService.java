package com.khjxiaogu.autoupdate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.InflaterInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.khjxiaogu.autoupdate.utils.FileUtil;
import com.khjxiaogu.autoupdate.utils.SupplierList;
import com.khjxiaogu.autoupdate.utils.Variables;
import com.khjxiaogu.autoupdate.utils.Version;
import com.khjxiaogu.autoupdate.utils.ZipMaker;

import cpw.mods.modlauncher.Launcher;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.Environment;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;

public class UpdateService implements IModLocator {
	IModLocator forge;
	Path modDirBase;
	List<Path> fileToDelete = new ArrayList<>();
	CompletableFuture<InputStream> updateTask;
	Consumer<String> progressScreen;

	private static final Logger LOGGER = LogManager.getLogger("Auto Update");
	Path fd;
	ZipMaker bkf;
	Set<String> added = new HashSet<>();
	Set<String> ignores;
	Version remote = null;
	Version local;
	public UpdateService() {
		this.progressScreen = Launcher.INSTANCE.environment().getProperty(Environment.Keys.PROGRESSMESSAGE.get())
				.orElse(msg -> {
				}).andThen(msg -> LOGGER.info(msg));
		fd = FMLPaths.GAMEDIR.get();
		
		final List<String> ignoredMods = new ArrayList<>();
		ignoredMods.addAll(Arrays.asList(System.getProperty("autoupdate.ignoreMods","").split(",")));
		ignoredMods.removeIf(t->t.isEmpty());
		if(!ignoredMods.isEmpty()) {
			ignores=new HashSet<>(ignoredMods);
		}
		final File lastver = new File(FMLPaths.CONFIGDIR.get().toFile(), ".lastversion");
		String token=System.getProperty("autoupdate.token");
		String channel=System.getProperty("autoupdate.channel");
		boolean keepCheck=Boolean.valueOf(System.getProperty("autoupdate.keepCheck", "false"));
		modDirBase = FMLPaths.MODSDIR.get();
		Version mod;
		if (token==null||channel==null)
			return;
		this.updateTask = CompletableFuture.supplyAsync(() -> {

			progressScreen.accept("Checking for Update");
			try {
				remote = Version.parse(FileUtil
						.readStringOrEmpty(fetchAnyDomain("/data/channel/" + channel + "")));
				if (local.laterThan(remote)) {
					progressScreen.accept("Latest");
					return null;
				}
				progressScreen.accept("Outdated");
				return FileUtil.readStringOrEmpty(fetchAnyDomain("/data/" + token));
			} catch (Exception e) {

				LOGGER.catching(e);
				return null;
			}
		}).handleAsync((data, err) -> {
			if (data == null || data.isEmpty())
				return null;
			try {
				progressScreen.accept("Auto Update is fetching latest");
				return FileUtil.fetch(data);
			} catch (IOException e1) {
				LOGGER.catching(e1);
				return null;
			}
		}).whenComplete((data, err) -> {
			if (data == null)
				return;
			try (Scanner delta = new Scanner(new InflaterInputStream(data))) {
				try {
					if (bkf == null) {
						createBackup();
					}
				} catch (IOException e2) {
					LOGGER.error("Cannot create backup file");
					return;
				}
				outer:while (delta.hasNext()) {
					String dl = delta.nextLine();
					int cpos = dl.indexOf('#');
					if (cpos != -1)
						dl = dl.substring(0, cpos).trim();
					if (dl.isEmpty())
						continue;
					char op = dl.charAt(0);
					String[] datas = dl.substring(1).split("\\?");
					String path = datas[0];
					if (op == '-') {
						Path todel = fd.resolve(path).normalize();
						if (!todel.startsWith(fd)) {// do security check to prevent malicious modification
							LOGGER.warn(
									"ILLEGAL PATH ACCESS OUTSIDE /.minecraft! Consider this mod has been modified maliciously.");
							continue;
						}
						File f = todel.toFile();
						if (f.exists())
							try {
								progressScreen.accept("Deleting " + f.getName());
								delete(f);
							} catch (IOException e) {
								throw new RuntimeException(e);
							}
					} else if (op == '+') {
						Path toadd = fd.resolve(path).normalize();
						if (!toadd.startsWith(fd)) {// do security check to prevent malicious modification
							LOGGER.warn(
									"ILLEGAL PATH ACCESS OUTSIDE /.minecraft! Consider this mod has been modified maliciously.");
							continue;
						}
						String digest = datas[1];
						String download = datas[2];
						String s = toadd.getFileName().toString().toLowerCase();
						if (s.endsWith(".jar"))
							for (String x : ignoredMods)
								if (s.startsWith(x)) {
									LOGGER.info("Ignored file "+s);
									continue outer;
								}
						if (!toadd.toFile().exists() || !SHA256(toadd.toFile()).equals(digest))
							try {
								progressScreen.accept("Downloading " + toadd.getFileName().toString());
								if (download.startsWith("!"))
									FileUtil.transfer(new InflaterInputStream(FileUtil.fetch(download.substring(1))),
											overwrite(path));
								else
									FileUtil.transfer(FileUtil.fetch(download), overwrite(path));
								progressScreen.accept("Download complete: " + toadd.getFileName().toString());
							} catch (IOException e) {
								throw new RuntimeException(e);
							}

					}
				}
			}catch(Exception ex) {
				ex.printStackTrace();
				LOGGER.warn("fetch failed");
				try {
					bkf.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				return;
			}
			progressScreen.accept("Preparing final Updates");
			Variables.setUpdateSuccess(true);

			LOGGER.info("Update Successful!");
			LOGGER.info("Old configuration files have been backed-up to" + Variables.getBackupPath());
			
			try {
				FileUtil.transfer(remote.getOriginal(), lastver);
			} catch (IOException e) {
				LOGGER.catching(e);
			}
			try {
				bkf.close();
			} catch (IOException e) {
				LOGGER.catching(e);
			}
		});

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			fileToDelete.removeIf(p -> {
				try {
					Files.deleteIfExists(p);
					return true;
				} catch (IOException problems) {
					return false;
				}
			});
			if (!fileToDelete.isEmpty())
				try (PrintStream ps = new PrintStream(new File(FMLPaths.CONFIGDIR.get().toFile(), "ignoredlist.txt"))) {
					for (Path p : fileToDelete) {
						ps.println(p);
					}
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}, "TSS Delete Outdated files"));
		this.updateTask.join();// have to block mt.
		if(channel!=null&&keepCheck) {
			local=remote;
			Thread ud=new Thread(()-> {
				while(true) {
					try {
						Thread.sleep(60000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						return;
					}
					remote = Version.parse(FileUtil
							.readStringOrEmpty(fetchAnyDomain("/data/channel/" + channel)));
					if(!local.laterThan(remote))
						break;
				}
				LOGGER.info("Newer version found");
				Variables.restart();
			});
			ud.setDaemon(true);
			ud.setName("Update checker thread");
			ud.start();
		}
	}
	protected MessageDigest getDigest() throws NoSuchAlgorithmException {
		return MessageDigest.getInstance("SHA-256");
	}

	public static String bytesToHex(byte[] hash) {
		StringBuilder hexString = new StringBuilder();
		for (int i = 0; i < hash.length; i++) {
			String hex = Integer.toHexString(0xff & hash[i]);
			if (hex.length() == 1) {
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}

	private void createBackup() throws IOException {
		Path backup = fd.resolve(
				"confbackup/backup-" + (new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")).format(new Date()) + ".zip");
		backup.toFile().getParentFile().mkdirs();
		Variables.setBackupPath(backup);
		bkf = new ZipMaker(backup.toFile(), fd);
	}

	public InputStream fetchAnyDomain(String path) {
		String[] paths=System.getProperty("autoupdate.sites","").split(",");
		for(int i=0;i<paths.length;i++) {
			paths[i]+=path;
		}
		return new <String>SupplierList<InputStream>(FileUtil::fetch,paths).get();
	}

	public String SHA256(File f) {

		try {
			MessageDigest digest = getDigest();
			try {
				return bytesToHex(digest.digest(FileUtil.readIgnoreSpace(f)));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (NoSuchAlgorithmException e) {
		}
		return "";
	}

	@Override
	public List<IModFile> scanMods() {

		return forge.scanMods().stream().filter(mf -> !fileToDelete.contains(mf.getFilePath()))
				.collect(Collectors.toList());
	}

	@Override
	public String name() {
		return "Auto Update";
	}

	@Override
	public Path findPath(IModFile modFile, String... path) {
		return forge.findPath(modFile, path);
	}

	@Override
	public void scanFile(IModFile modFile, Consumer<Path> pathConsumer) {
		forge.scanFile(modFile, pathConsumer);
	}

	@Override
	public Optional<Manifest> findManifest(Path file) {
		return forge.findManifest(file);
	}

	@Override
	public void initArguments(Map<String, ?> arguments) {
		forge = Launcher.INSTANCE.environment().getProperty(Environment.Keys.MODDIRECTORYFACTORY.get())
				.orElseThrow(() -> new RuntimeException("Cannot find ModDirectoryFactory"))
				.build(this.modDirBase, "Auto Update");
		forge.initArguments(arguments);

	}

	@Override
	public boolean isValid(IModFile modFile) {
		return (!fileToDelete.contains(modFile.getFilePath())) && forge.isValid(modFile);
	}

	File overwrite(String rfn) throws IOException {
		File towrite = fd.resolve(rfn).toFile();
		if (!rfn.endsWith(".jar"))
			bkf.add(towrite);
		towrite.getParentFile().mkdirs();
		return towrite;
	}

	void overwrite(String rfn, byte[] bs) throws IOException {
		if (bs == null)
			return;

		File towrite = fd.resolve(rfn).toFile();
		if (!rfn.endsWith(".jar"))
			bkf.add(towrite);
		towrite.getParentFile().mkdirs();
		try (FileOutputStream fos = new FileOutputStream(towrite)) {
			fos.write(bs);
		}
	}

	void delete(File f) throws IOException {
		if (f.isDirectory()) {
			File[] fs = f.listFiles();
			if (fs != null) {
				for (File file : fs) {
					delete(file);
				}
			}
		} else {
			if (f.getName().endsWith(".jar")) {
				f.delete();
				fileToDelete.add(f.toPath().toAbsolutePath());
			} else {
				bkf.add(f);
				f.delete();
			}
		}

	}
}
