package org.myrobotlab.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.myrobotlab.cmdline.CmdLine;
import org.myrobotlab.codec.CodecJson;
import org.myrobotlab.codec.CodecUtils;
import org.myrobotlab.framework.Platform;
import org.myrobotlab.framework.ProcessData;
import org.myrobotlab.framework.Service;
import org.myrobotlab.framework.ServiceType;
import org.myrobotlab.framework.Status;
import org.myrobotlab.framework.Message;
import org.myrobotlab.framework.repo.GitHub;
import org.myrobotlab.framework.repo.Repo;
import org.myrobotlab.framework.repo.ServiceData;
import org.myrobotlab.io.FileIO;
import org.myrobotlab.logging.LoggerFactory;
import org.myrobotlab.logging.Logging;
import org.myrobotlab.net.Http;
import org.slf4j.Logger;

import com.google.gson.internal.LinkedTreeMap;

/**
 * @author GroG
 * 
 *         Agent is responsible for processes in the same way Runtime is
 *         responsible for Services.
 * 
 *         List of responsibilities:
 * 
 *         Start an MyRobotLab process with the needed command line parameters.
 *         This includes classpath /libraries/jar/* and
 *         java.jni/jna.home=/libraries/native
 *
 *         Memory xms xmx should be adjusted if necessary
 * 
 *         Pass command line parameters to new instance or MyRobotLab
 * 
 *         Manage testing
 * 
 *         Several modes exist - normal = set env and keep process in map, with
 *         re-directed stdin stdout & stderr streams envOnly = set the correct
 *         environment then terminate
 * 
 * 
 *         default is start a new process with relayed cmdline and redirect
 *         stdin stout & stderr streams, terminate if no subprocesses exist
 * 
 *         =================================================================== *
 *         References :
 *
 *         http://www.excelsior-usa.com/articles/java-to-exe.html
 *
 *         possible small wrappers mac / linux / windows
 *         http://mypomodoro.googlecode
 *         .com/svn-history/r89/trunk/src/main/java/org
 *         /mypomodoro/util/Restart.java
 *
 *         http://java.dzone.com/articles/programmatically-restart-java
 *         http://stackoverflow
 *         .com/questions/3468987/executing-another-application-from-java
 *
 *
 *         TODO - ARMV 6 7 8 ??? -
 *         http://www.binarytides.com/linux-cpu-information/ - lscpu
 *
 *         Architecture: armv7l Byte Order: Little Endian CPU(s): 4 On-line
 *         CPU(s) list: 0-3 Thread(s) per core: 1 Core(s) per socket: 1
 *         Socket(s): 4
 *
 *
 *         TODO - soft floating point vs hard floating point readelf -A
 *         /proc/self/exe | grep Tag_ABI_VFP_args soft = nothing hard =
 *         Tag_ABI_VFP_args: VFP registers
 *
 *         PACKAGING jsmooth - windows only javafx - 1.76u - more dependencies ?
 *         http://stackoverflow.com/questions/1967549/java-packaging-tools-
 *         alternatives-for-jsmooth-launch4j-onejar
 *
 *         TODO classpath order - for quick bleeding edge updates? rsync
 *         exploded classpath
 *
 *         TODO - check for Java 1.7 or > addShutdownHook check for network
 *         connectivity TODO - proxy -Dhttp.proxyHost=webproxy
 *         -Dhttp.proxyPort=80 -Dhttps.proxyHost=webproxy -Dhttps.proxyPort=80
 *         -Dhttp.proxyUserName="myusername" -Dhttp.proxyPassword="mypassword"
 * 
 *         TODO? how to get vm args http:*
 *         stackoverflow.com/questions/1490869/how-to-get
 *         -vm-arguments-from-inside-of-java-application http:*
 *         java.dzone.com/articles/programmatically-restart-java http:*
 *         stackoverflow.com
 *         /questions/9911686/getresource-some-jar-returns-null-although
 *         -some-jar-exists-in-geturls RuntimeMXBean runtimeMxBean =
 *         ManagementFactory.getRuntimeMXBean(); List<String> arguments =
 *         runtimeMxBean.getInputArguments();
 * 
 *         TODO - on java -jar myrobotlab.jar | make a copy if agent.jar does
 *         not exist.. if it does then spawn the Agent there ... it would make
 *         upgrading myrobotlab.jar "trivial" !!!
 * 
 *         TO TEST - -agent "-test -logLevel WARN"
 */
public class Agent extends Service {

	private static final long serialVersionUID = 1L;

	public final static Logger log = LoggerFactory.getLogger(Agent.class);

	static HashSet<String> dependencies = new HashSet<String>();

	static HashMap<Integer, ProcessData> processes = new HashMap<Integer, ProcessData>();

	static List<String> agentJVMArgs = new ArrayList<String>();
	static transient SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd:HH:mm:ss");

	static Platform platform = Platform.getLocalInstance();

	static CmdLine runtimeArgs;

	static String currentBranch = platform.getBranch();
	static String currentVersion = platform.getVersion();

	static String MSGS_DIR = "msgs";

	// FIXME - all update functionality will need to be moved to Runtime
	// it should take parameters such that it will be possible at some point to
	// do an update
	// from a child process & update the agent :)

	static HashSet<String> possibleVersions = new HashSet<String>();

	// String lastBranch = null;
	// WebGui webmin = null; can't have a peer untile nettosphere is part of
	// base build
	// boolean updateRestartProcesses = false;

	static String updateUrl = "http://mrl-bucket-01.s3.amazonaws.com/current/%s";
	static String jarUrlTemplate = "http://mrl-bucket-01.s3.amazonaws.com/current/%s/myrobotlab.jar";
	static String versionUrlTemplate = "http://mrl-bucket-01.s3.amazonaws.com/current/%s/version.txt";
	static String versionsListUrlTemplate = "http://mrl-bucket-01.s3.amazonaws.com/";

	static boolean checkRemoteVersions = false;

	static String latestRemote;

	static Agent agent;

	public Agent(String n) {
		super(n);
		log.info("Agent {} Pid {} is alive", n, Runtime.getInstance().getPid());
		agentJVMArgs = Runtime.getJVMArgs();
		if (currentBranch == null) {
			currentBranch = platform.getBranch();
		}
		if (currentVersion == null) {
			currentVersion = platform.getVersion();
		}

		// add my branch
		// possibleBranches.add(agentBranch);

		if (checkRemoteVersions) {
			// TODO - turn into asynchronous call
			// so no connection will not lead to an irritating 'wait' timeout
			getRemoteBranches();
		}
		setBranch(currentBranch);
		agent = this;
		File folder = new File(MSGS_DIR);
		folder.mkdirs();		
	}

	/**
	 * simple file to byte array
	 * 
	 * @param file
	 *            - file to read
	 * @return byte array of contents
	 * @throws IOException
	 */
	static public final byte[] toByteArray(File file) throws IOException {

		FileInputStream fis = null;
		byte[] data = null;

		fis = new FileInputStream(file);
		data = toByteArray(fis);

		fis.close();

		return data;
	}

	/**
	 * IntputStream to byte array
	 * 
	 * @param is
	 * @return
	 */
	static public final byte[] toByteArray(InputStream is) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {

			int nRead;
			byte[] data = new byte[16384];

			while ((nRead = is.read(data, 0, data.length)) != -1) {
				baos.write(data, 0, nRead);
			}

			baos.flush();
			baos.close();
			return baos.toByteArray();
		} catch (Exception e) {
			Logging.logError(e);
		}

		return null;
	}

	public void scanForMsgs() {
		File folder = new File(MSGS_DIR);
		File[] listOfFiles = folder.listFiles();

		for (int i = 0; i < listOfFiles.length; i++) {
			File json = listOfFiles[i];
			if (json.isFile() && json.getName().endsWith(".json")) {
				try {
					Message msg = CodecUtils.fromJson(new String(toByteArray(json)), Message.class);
					json.delete();
					in(msg);
				} catch (Exception e) {
					log.error("msgs/{} threw", json);
				}
			}
		}
	}

	// revert ! only 1 global autoUpdate - all processes - not Agent (yet)
	static public void autoUpdate(boolean b) {

		if (agent != null) {
			String name = String.format("%s.timer.processUpdates", agent.getName());

			if (b) {
				agent.addTask(name, 1000 * 60, 0, "processUpdates");
			} else {
				agent.purgeTask(name);
			}
		}
	}

	static public void startWebGui() {
		try {
			// no reference at all to WebGui
			// look ma no reference !
			WebGui webgui = (WebGui) Runtime.create("webmin", "WebGui");
			// send("webmin", "setPort", 8887);
			webgui.setPort(8887);
			Runtime.start("webmin", "WebGui");

		} catch (Exception e) {
			Logging.logError(e);
		}

	}

	/**
	 * checks the current branch looks if the verstion.txt has been changed
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws URISyntaxException
	 */
	static synchronized public void processUpdates() throws IOException, URISyntaxException, InterruptedException {

		String remoteVersion = getLatestRemoteVersion(currentBranch);
		if (remoteVersion == null) {
			log.error("checkForUpdates %s is null", currentBranch);
		} else {
			log.info("found remote version {}", remoteVersion);
			File checkIfWeHaveJar = new File(String.format("%s/myrobotlab.%s.jar", currentBranch, remoteVersion));
			if (!checkIfWeHaveJar.exists()) {
				log.info("downloading remote version {}", remoteVersion);
				downloadLatest(currentBranch);
			}

			for (Integer key : processes.keySet()) {
				ProcessData process = processes.get(key);
				if (!currentBranch.equals(process.branch)) {
					log.info("skipping update of {} because its on branch {}", process.id, process.branch);
					continue;
				}

				if (remoteVersion.equals(process.version)) {
					log.info("skipping update of {} {} because its already version {}", process.id, process.name,
							process.version);
					continue;
				}

				// FIXME - it would be nice to send a SIG_TERM to
				// the process before we kill the jvm
				// process.process.getOutputStream().write("/Runtime/releaseAll".getBytes());
				process.version = remoteVersion;
				if (process.isRunning()) {
					restart(process.id);
				}
			}
		}
	}

	/**
	 * if there is a single instance - just restart it ...
	 * 
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws InterruptedException
	 */
	static public synchronized void restart() throws IOException, URISyntaxException, InterruptedException {
		for (Integer pid : processes.keySet()) {
			restart(pid);
		}		
	}

	/**
	 * 
	 * @param id
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws URISyntaxException
	 */
	static public synchronized void restart(Integer id) throws IOException, URISyntaxException, InterruptedException {
		// if null or processes.size == 0 then self
		if (id == null || processes.size() == 0) {
			log.info("restarting self");
			spawn(Runtime.getGlobalArgs());
			terminateSelfOnly();
		} else {
			ProcessData pd = processes.get(id);
			log.info("restarting process {}", id);
			pd.setRestarting();
			kill(id);
			spawn2(processes.get(id));
		}
	}

	/**
	 * return a non-running process structure from an existing one with a new id
	 * 
	 * @param id
	 * @return
	 */
	static public ProcessData copy(Integer id) {
		if (!processes.containsKey(id)) {
			log.error("cannot copy %d does not exist", id);
			return null;
		}
		ProcessData pd = processes.get(id);
		ProcessData pd2 = new ProcessData(pd);
		pd2.startTs = null;
		pd2.stopTs = null;
		pd2.id = getNextProcessId();
		processes.put(pd2.id, pd2);
		if (agent != null) {
			agent.broadcastState();
		}
		return pd2;
	}

	static public void copyAndStart(Integer id) throws IOException {
		// returns a non running copy with new process id
		// on the processes list
		ProcessData pd2 = copy(id);
		spawn2(pd2);
		if (agent != null) {
			agent.broadcastState();
		}
	}

	static public void downloadLatest(String branch) throws IOException {
		String version = getLatestRemoteVersion(branch);
		log.info("downloading version {} /{}", version, branch);
		byte[] myrobotlabjar = getLatestRemoteJar(branch);
		if (myrobotlabjar == null) {
			throw new IOException("could not download");
		}
		log.info("{} bytes", myrobotlabjar.length);

		/*
		 * File archive = new File(String.format("%s/archive", branch));
		 * archive.mkdirs();
		 */

		FileOutputStream fos = new FileOutputStream(String.format("%s/myrobotlab.%s.jar", branch, version));
		fos.write(myrobotlabjar);
		fos.close();
	}

	static public String formatList(ArrayList<String> args) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < args.size(); ++i) {
			log.info(args.get(i));
			sb.append(String.format("%s ", args.get(i)));
		}
		return sb.toString();
	}

	/**
	 * gets id from name
	 * 
	 * @param name
	 * @return
	 */
	static public Integer getId(String name) {
		for (Integer pid : processes.keySet()) {
			if (pid.equals(name)) {
				return processes.get(pid).id;
			}
		}
		return null;
	}

	// FIXME - should just be be saveRemoteJar() - but shouldn't be from
	// multiple threads
	static public byte[] getLatestRemoteJar(String branch) {
		return Http.get(String.format(jarUrlTemplate, branch));
	}

	static public String getLatestRemoteVersion(String branch) {
		byte[] data = Http.get(String.format(versionUrlTemplate, branch));
		if (data != null) {
			return new String(data);
		}
		return null;
	}

	/**
	 * gets name from id
	 * 
	 * @param id
	 * @return
	 */
	static public String getName(Integer id) {
		for (Integer pid : processes.keySet()) {
			if (pid.equals(id)) {
				return processes.get(pid).name;
			}
		}

		return null;
	}

	static synchronized public Integer getNextProcessId() {
		Integer ret = 0;
		for (int i = 0; i < processes.size(); ++i) {
			if (!processes.containsKey(ret)) {
				return ret;
			}
			ret += 1;
		}
		return ret;
	}

	static public Set<String> getRemoteBranches() {
		Set<String> possibleBranches = new HashSet<String>();
		try {
			// TODO - all http gets use HttpClient static methods and promise
			// for asynchronous
			// get gitHub's branches
			byte[] r = Http.get(GitHub.BRANCHES);
			if (r != null) {
				String branches = new String(r);
				CodecJson decoder = new CodecJson();
				// decoder.decodeArray(Branch)
				Object[] array = decoder.decodeArray(branches);
				for (int i = 0; i < array.length; ++i) {
					@SuppressWarnings("unchecked")
					LinkedTreeMap<String, String> m = (LinkedTreeMap<String, String>) array[i];
					if (m.containsKey("name")) {
						possibleBranches.add(m.get("name").toString());
					}
				}
			}
		} catch (Exception e) {
			Logging.logError(e);
		}
		return possibleBranches;
	}

	static synchronized public HashSet<String> getPossibleVersions() {

		// clear versions
		possibleVersions.clear();

		// get local versions
		File branchFolder = new File(currentBranch);
		if (!branchFolder.isDirectory()) {
			log.error("{} not a directory", currentBranch);
		} else {
			File[] listOfFiles = branchFolder.listFiles();
			for (int i = 0; i < listOfFiles.length; ++i) {
				File file = listOfFiles[i];
				if (!file.isDirectory()) {
					if (file.getName().startsWith("myrobotlab.")) {
						String version = getFileVersion(file.getName());
						if (version != null) {
							possibleVersions.add(version);
						}
					}
				}
			}
		}

		// TODO !!! make asynchronous promise !!!!
		String remote = getLatestRemoteVersion(currentBranch);
		if (remote != null) {
			if (!possibleVersions.contains(remote)) {
				possibleVersions.add(remote);
				if (agent != null) {
					agent.invoke("newVersionAvailable", remote);
				}
				latestRemote = remote;
			}
		}
		return possibleVersions;
	}

	static public String newVersionAvailable() {
		return latestRemote;
	}

	static public String getFileVersion(String name) {
		if (!name.startsWith("myrobotlab.")) {
			return null;
		}

		String[] parts = name.split("\\.");
		if (parts.length != 5) {
			return null;
		}

		String version = String.format("%s.%s.%s", parts[1], parts[2], parts[3]);

		return version;
	}

	/**
	 * get a list of all the processes currently governed by this Agent
	 * 
	 * @return
	 */
	static public HashMap<Integer, ProcessData> getProcesses() {
		return processes;
	}

	/*
	 * - REMOVE only Runtime should install public List<Status> install(String
	 * fullType) { List<Status> ret = new ArrayList<Status>();
	 * ret.add(Status.info("install %s", fullType)); try { Repo repo =
	 * Repo.getLocalInstance();
	 * 
	 * if (!repo.isServiceTypeInstalled(fullType)) { repo.install(fullType); if
	 * (repo.hasErrors()) { ret.addAll(repo.getErrors()); }
	 * 
	 * } else { log.info("installed {}", fullType); } } catch (Exception e) {
	 * ret.add(Status.error(e)); } return ret; }
	 */

	static public Integer kill(Integer id) {
		if (processes.containsKey(id)) {
			if (agent != null) {
				agent.info("terminating %s", id);
			}
			ProcessData process = processes.get(id);
			process.process.destroy();
			process.state = ProcessData.STATE_STOPPED;

			if (process.monitor != null) {
				process.monitor.interrupt();
				process.monitor = null;
			}
			// remove(processes.get(name));
			if (agent != null) {
				agent.info("%s haz beeen terminated", id);
				agent.broadcastState();
			}
			return id;
		}

		log.warn("%s? no sir, I don't know that punk...", id);

		return null;
	}

	/*
	 * BAD IDEA - data type ambiguity is a drag public Integer kill(String name)
	 * { return kill(getId(name)); }
	 */

	static public void killAll() {
		for (Integer id : processes.keySet()) {
			kill(id);
		}
		log.info("no survivors sir...");
		if (agent != null) {
			agent.broadcastState();
		}
	}

	static public void killAndRemove(Integer id) {
		if (processes.containsKey(id)) {
			kill(id);
			processes.remove(id);
			if (agent != null) {
				agent.broadcastState();
			}
		}
	}

	/**
	 * list processes
	 */
	static public String[] lp() {
		Object[] objs = processes.keySet().toArray();
		String[] pd = new String[objs.length];
		for (int i = 0; i < objs.length; ++i) {
			Integer id = (Integer) objs[i];
			ProcessData p = processes.get(id);
			pd[i] = String.format("%d - %s [%s - %s]", id, p.name, p.branch, p.version);
		}
		return pd;
	}

	static public Integer publishTerminated(Integer id) {
		log.info("publishTerminated - terminated %d %s - restarting", id, getName(id));

		
		if (!processes.containsKey(id)) {
			log.error("processes {} not found");
			return id;
		}

		// if you don't fork with Agent allowed to
		// exist without instances - then
		if (!runtimeArgs.containsKey("-fork")) {
			// spin through instances - if I'm the only
			// thing left - terminate
			boolean processesStillRunning = false;
			for (ProcessData pd : processes.values()) {
				if (pd.isRunning() || pd.isRestarting()) {
					processesStillRunning = true;
					break;
				}
			}

			if (!processesStillRunning) {
				shutdown();
			}
		}

		if (agent != null) {
			agent.broadcastState();
		}
	
		return id;
	}

	/**
	 * This is a great idea & test - because we want complete control over
	 * environment and dependencies - the ability to purge completely - and
	 * start from the beginning - but it should be in another service and not
	 * part of the Agent. The 'Test' service could use Agent as a peer
	 * 
	 * @return
	 */
	static public List<Status> serviceTest() {

		List<Status> ret = new ArrayList<Status>();
		// CLEAN FOR TEST METHOD

		// FIXME DEPRECATE !!!
		// RUNTIME is responsible for running services
		// REPO is responsible for possible services
		// String[] serviceTypeNames =
		// Runtime.getInstance().getServiceTypeNames();

		HashSet<String> skipTest = new HashSet<String>();

		skipTest.add("org.myrobotlab.service.Runtime");
		skipTest.add("org.myrobotlab.service.OpenNi");

		/*
		 * skipTest.add("org.myrobotlab.service.Agent");
		 * skipTest.add("org.myrobotlab.service.Incubator");
		 * skipTest.add("org.myrobotlab.service.InMoov"); // just too big and
		 * complicated at the moment
		 * skipTest.add("org.myrobotlab.service.Test");
		 * skipTest.add("org.myrobotlab.service.Cli"); // ?? No ?
		 */

		long installTime = 0;
		Repo repo = Runtime.getInstance().getRepo();
		ServiceData serviceData = ServiceData.getLocalInstance();
		ArrayList<ServiceType> serviceTypes = serviceData.getServiceTypes();

		ret.add(Status.info("serviceTest will test %d services", serviceTypes.size()));
		long startTime = System.currentTimeMillis();
		ret.add(Status.info("startTime", "%d", startTime));

		for (int i = 0; i < serviceTypes.size(); ++i) {

			ServiceType serviceType = serviceTypes.get(i);

			// TODO - option to disable
			if (!serviceType.isAvailable()) {
				continue;
			}
			// serviceType = "org.myrobotlab.service.OpenCV";

			if (skipTest.contains(serviceType.getName())) {
				log.info("skipping %s", serviceType.getName());
				continue;
			}

			try {

				// agent.serviceTest(); // WTF?
				// status.addInfo("perparing clean environment for %s",
				// serviceType);

				// clean environment
				// FIXME - optimize clean

				// SUPER CLEAN - force .repo to clear !!
				// repo.clearRepo();

				// less clean but faster
				// repo.clearLibraries();
				// repo.clearServiceData();

				// comment all out for dirty

				// install Test dependencies
				long installStartTime = System.currentTimeMillis();
				repo.install("org.myrobotlab.service.Test");
				repo.install(serviceType.getName());
				installTime += System.currentTimeMillis() - installStartTime;
				// clean test.json part file

				// spawn a test - attach to cli - test 1 service end to end
				// ,"-invoke", "test","test","org.myrobotlab.service.Clock"
				Process process = spawn(new String[] { "-runtimeName", "testEnv", "-service", "test", "Test",
						"-logLevel", "WARN", "-noEnv", "-invoke", "test", "test", serviceType.getName() });

				process.waitFor();

				// destroy - start again next service
				// wait for partFile report .. test.json
				// NOT NEEDED - foreign process has ended
				byte[] data = FileIO.loadPartFile("test.json", 60000);
				if (data != null) {
					String test = new String(data);
					Status testResult = CodecUtils.fromJson(test, Status.class);
					if (testResult.isError()) {
						ret.add(testResult);
					}
				} else {
					Status.info("could not get results");
				}
				// destroy env
				kill(getId("testEnv"));

			} catch (Exception e) {

				ret.add(Status.error(e));
				continue;
			}
		}

		ret.add(Status.info("installTime", "%d", installTime));

		ret.add(Status.info("installTime %d", installTime));
		ret.add(Status.info("testTimeMs %d", System.currentTimeMillis() - startTime));
		ret.add(Status.info("testTimeMinutes %d",
				TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - startTime)));
		ret.add(Status.info("endTime %d", System.currentTimeMillis()));

		try {
			FileIO.savePartFile(new File("fullTest.json"), CodecUtils.toJson(ret).getBytes());
		} catch (Exception e) {
			Logging.logError(e);
		}

		return ret;
	}

	static public String setBranch(String branch) {
		currentBranch = branch;
		if (checkRemoteVersions) {
			getPossibleVersions();
		}
		return currentBranch;
	}

	static public Map<String, String> setEnv(Map<String, String> env) {
		Platform platform = Platform.getLocalInstance();
		String platformId = platform.getPlatformId();
		if (platform.isLinux()) {
			String ldPath = String.format("'pwd'/libraries/native:'pwd'/libraries/native/%s:${LD_LIBRARY_PATH}",
					platformId);
			env.put("LD_LIBRARY_PATH", ldPath);
		} else if (platform.isMac()) {
			String dyPath = String.format("'pwd'/libraries/native:'pwd'/libraries/native/%s:${DYLD_LIBRARY_PATH}",
					platformId);
			env.put("DYLD_LIBRARY_PATH", dyPath);
		} else if (platform.isWindows()) {
			// this just borks the path in Windows - additionally (unlike Linux)
			// - i don't think you need native code on the PATH
			// and Windows does not have a LD_LIBRARY_PATH
			// String path =
			// String.format("PATH=%%CD%%\\libraries\\native;PATH=%%CD%%\\libraries\\native\\%s;%%PATH%%",
			// platformId);
			// env.put("PATH", path);
			// we need to sanitize against a non-ascii username
			// work around for Jython bug in 2.7.0...
			env.put("APPDATA", "%%CD%%");
		} else {
			log.error("unkown operating system");
		}

		return env;
	}

	static public void shutdown() {
		log.info("terminating others");
		killAll();
		log.info("terminating self ... goodbye...");
		Runtime.exit();
	}

	static public synchronized Process spawn(String[] in) throws IOException, URISyntaxException, InterruptedException {

		// runtime vs develop time
		String jarPath = null;
		if (FileIO.isJar()) {
			log.info("I am a jar - must be runtime");
			// runtime
			jarPath = FileIO.getRoot();
		} else {
			// develop time (post build)
			log.info("I am not a jar - must be develop time");
			String test = "build/lib/myrobotlab.jar";
			File recentlyBuilt = new File(test);
			if (!recentlyBuilt.exists()) {
				log.error("umm .. I need to start a jar - would you mind building one with build.xml");
				log.error(
						"perhaps in the future I can change all the classpaths etc to start an instances with the bin classes - but no time to do that now");
				log.error("adios... hope we meet again...");
				System.exit(-1);
			}

			jarPath = new File(test).getAbsolutePath();
		}

		return spawn(jarPath, in);
	}

	/**
	 * Responsibility - This method will always call Runtime. To start Runtime
	 * correctly environment must correctly be setup
	 * 
	 * @param jarPath
	 *            - Absolute path to myrobotlab.jar
	 * @param in
	 *            - command line parameters
	 * @return
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws InterruptedException
	 */
	static public synchronized Process spawn(String jarPath, String[] in)
			throws IOException, URISyntaxException, InterruptedException {

		// handle url to path utf-8 crazyness here
		// getCodeSource().getLocation().toURI().getPath()
		File jarPathDir = new File(jarPath);

		// jarPathDir.getAbsolutePath() is absolutely necessary - relative paths
		// will not work
		ProcessData pd = new ProcessData(agent, jarPathDir.getAbsolutePath(), in, currentBranch, currentVersion);

		CmdLine cmdline = new CmdLine(in);
		if (cmdline.hasSwitch("-autoUpdate")) {
			autoUpdate(true);
		}

		log.info(String.format("Agent starting spawn %s", formatter.format(new Date())));
		log.info("in args {}", Arrays.toString(in));

		// String jvmMemory = "-Xmx2048m -Xms256m";
		long totalMemory = Runtime.getTotalPhysicalMemory();
		if (totalMemory == 0) {
			log.info("could not get total physical memory");
		} else {
			log.info("total physical memory returned is {} Mb", totalMemory / 1048576);
		}

		// need to fill it out as best you can before submitting to spawn2
		return spawn2(pd);
	}

	static public synchronized Process spawn2(ProcessData pd) throws IOException {

		log.info("============== spawn begin ==============");

		String runtimeName = pd.name;

		// this needs cmdLine
		String[] cmdLine = pd.buildCmdLine();
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < cmdLine.length; ++i) {
			sb.append(cmdLine[i]);
			sb.append(" ");
		}

		// add process id

		// log.info(String.format("in %s spawning -> [%s]", b.getAbsolutePath(),
		// sb.toString()));
		ProcessBuilder builder = new ProcessBuilder(cmdLine);

		// setting working directory to wherever the jar is...
		String spawnDir = new File(pd.jarPath).getParent();
		builder.directory(new File(spawnDir));

		log.info(String.format("in %s spawning -> [%s]", spawnDir, sb.toString()));

		// environment variables setup
		setEnv(builder.environment());

		Process process = builder.start();
		pd.process = process;
		pd.startTs = System.currentTimeMillis();
		pd.monitor = new ProcessData.Monitor(pd);
		pd.monitor.start();

		pd.state = ProcessData.STATE_RUNNING;
		if (pd.id == null) {
			pd.id = getNextProcessId();
		}
		if (processes.containsKey(pd.id)) {
			if (agent != null) {
				agent.info("restarting %d %s", pd.id, pd.name);
			}
		} else {
			if (agent != null) {
				agent.info("starting new %d %s", pd.id, pd.name);
			}
			processes.put(pd.id, pd);
		}

		// attach our cli to the latest instance
		// *** interesting - not processing input/output will block the thread
		// in the spawned process ***
		// which I assume is the beginning main thread doing a write to std::out
		// and it blocking before anything else can happen

		log.info("Agent finished spawn {}", formatter.format(new Date()));
		if (agent != null) {
			Cli cli = Runtime.getCli();
			cli.add(runtimeName, process.getInputStream(), process.getOutputStream());
			cli.attach(runtimeName);
			agent.broadcastState();
		}
		return process;

	}

	/**
	 * DEPRECATE ? spawn2 should do this checking ?
	 * 
	 * @param id
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws InterruptedException
	 */
	static public void start(Integer id) throws IOException, URISyntaxException, InterruptedException {
		if (!processes.containsKey(id)) {
			log.error("start process %s can not start - process does not exist", id);
			return;
		}

		ProcessData p = processes.get(id);
		if (p.isRunning()) {
			log.warn("process %s already started", id);
			return;
		}
		spawn2(p);
	}

	static public void terminateSelfOnly() {
		log.info("goodbye .. cruel world");
		System.exit(0);
	}

	static public void update() throws IOException {
		Platform platform = Platform.getLocalInstance();
		update(platform.getBranch());
	}

	static public void update(String branch) throws IOException {
		log.info("update({})", branch);
		// so we need to get the version of the jar contained in the {branch}
		// directory ..
		FileIO.extract(String.format("%s/myrobotlab.jar", branch), "resource/version.txt",
				String.format("%s/version.txt", branch));

		String currentVersion = FileIO.toString(String.format("%s/version.txt", branch));
		if (currentVersion == null) {
			log.error("{}/version.txt current version is null", branch);
			return;
		}
		// compare that with the latest http://s3/current/{branch}/version.txt
		// and figure

		String latestVersion = getLatestRemoteVersion(branch);
		if (latestVersion == null) {
			log.error("s3 version.txt current version is null", branch);
			return;
		}

		if (!latestVersion.equals(currentVersion)) {
			log.info("latest %s > current %s - updating", latestVersion, currentVersion);
			downloadLatest(branch);
		}

		// FIXME - restart processes
		// if (updateRestartProcesses) {

		// }

	}

	/**
	 * This static method returns all the details of the class without it having
	 * to be constructed. It has description, categories, dependencies, and peer
	 * definitions.
	 * 
	 * @return ServiceType - returns all the data
	 * 
	 */
	static public ServiceType getMetaData() {

		ServiceType meta = new ServiceType(Agent.class.getCanonicalName());
		meta.addDescription(
				"Agent - responsible for creating the environment and maintaining, tracking and terminating all processes");
		meta.addCategory("framework");
		meta.setSponsor("GroG");
		// meta.addPeer("webadmin", "WebGui", "webgui for the Agent");
		return meta;
	}
	
	public void startService(){
		super.startService();
		// addTask(1000, "scanForMsgs");
	}

	/**
	 * First method JVM executes when myrobotlab.jar is in jar form.
	 * 
	 * -agent "-logLevel DEBUG -service webgui WebGui"
	 * 
	 * @param args
	 */
	// FIXME - add -help
	// TODO - add jvm memory other runtime info
	// FIXME - a way to route parameters from command line to Agent vs Runtime -
	// the current concept is ok - but it does not work ..
	// make it work if necessary prefix everything by -agent-<...>
	public static void main(String[] args) {
		try {
			// System.out.println("Agent.main starting"); - with static args it
			// doesnt really 'start'

			// FIXME - I think the basic idea is to have
			// parameters route to Agent or to the target instance
			// initially I was thinking of having all agent parameters
			// in a -agent \"-param1 value1 -param2 value2\" -services gui GUI
			// .. instance params
			// but that didn't work due to the parsing of CmdLine ...
			// need a good solution

			// split agent commands from runtime co\mmands
			// String[] agentArgs = new String[0];
			ArrayList<String> inArgs = new ArrayList<String>();
			// -agent \"-params -service ... \" string encoded
			runtimeArgs = new CmdLine(args);

			if (runtimeArgs.containsKey("-?") || runtimeArgs.containsKey("-h") || runtimeArgs.containsKey("-help")
					|| runtimeArgs.containsKey("--help")) {
				Runtime.mainHelp();
				return;
			}

			if (runtimeArgs.containsKey("-version")) {
				System.out.println(String.format("%s branch %s version %s", platform.getBranch(),
						platform.getPlatformId(), platform.getVersion()));
				return;
			}

			// -service for Runtime -process a b c d :)
			if (runtimeArgs.containsKey("-agent")) {
				// List<String> list = runtimeArgs.getArgumentList("-agent");

				String tmp = runtimeArgs.getArgument("-agent", 0);
				String[] agentPassedArgs = tmp.split(" ");
				if (agentPassedArgs.length > 1) {
					for (int i = 0; i < agentPassedArgs.length; ++i) {
						inArgs.add(agentPassedArgs[i]);
					}
				} else {
					if (tmp != null) {
						inArgs.add(tmp);
					}
				}
			}

			// default args passed to runtime from Agent
			inArgs.add("-isAgent");

			// String[] agentArgs = inArgs.toArray(new String[inArgs.size()]);
			// CmdLine agentCmd = new CmdLine(agentArgs);

			// FIXME -isAgent identifier sent -- default to setting log name to
			// agent.log !!!
			// Runtime.setRuntimeName("bootstrap");
			// Runtime.main(agentArgs);
			// Agent agentx = (Agent) Runtime.start("agent", "Agent");

			Process p = null;

			if (runtimeArgs.containsKey("-test")) {
				serviceTest();
			} else {
				if (!runtimeArgs.containsKey("-fork")) {
					Runtime.start("agent", "Agent");
				}
				if (!runtimeArgs.containsKey("-client")) {
					p = spawn(args); // <-- agent's is now in charge of first
				} else {
					Runtime.start("cli", "Cli");
				}
			}

			// change of design - agent will try to shutdown
			// as soon as the mrl processes starts
			if (runtimeArgs.containsKey("-install")) {
				p.waitFor();
				shutdown();
			}

		} catch (Exception e) {
			log.error("unsuccessful spawn", e);
		} finally {
			// big hammer
			// System.out.println("Agent.main leaving");- with static args it
			// doesnt really 'start'
			// System.exit(0);
		}
	}

}
