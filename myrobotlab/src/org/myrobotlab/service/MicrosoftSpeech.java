/**
 *                    
 * @author Dom14 (at) myrobotlab.org
 *  
 * This file is part of MyRobotLab (http://myrobotlab.org).
 *
 * MyRobotLab is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version (subject to the "Classpath" exception
 * as provided in the LICENSE.txt file that accompanied this code).
 *
 * MyRobotLab is distributed in the hope that it will be useful or fun,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * All libraries in thirdParty bundle are subject to their own license
 * requirements - please refer to http://myrobotlab.org/libraries for 
 * details.
 * 
 * Enjoy !
 * 
 * */
package org.myrobotlab.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.lang.Runtime;

import org.myrobotlab.framework.Service;
import org.myrobotlab.framework.ServiceType;
import org.myrobotlab.logging.LoggerFactory;
import org.myrobotlab.logging.Logging;
import org.myrobotlab.service.data.AudioData;
import org.myrobotlab.service.interfaces.SpeechRecognizer;
import org.myrobotlab.service.interfaces.SpeechSynthesis;
import org.myrobotlab.service.interfaces.TextListener;
import org.slf4j.Logger;

public class MicrosoftSpeech extends Service implements TextListener, SpeechSynthesis {
  static final Logger log = LoggerFactory.getLogger(MicrosoftSpeech.class);

	// For compabilities
	private static final long serialVersionUID = 1L;
	private HashSet<String> voices = new HashSet<String>();

	private String TextPath = "";
	private String voice = "";

	public MicrosoftSpeech(String reservedKey) {
		super(reservedKey);
	}
	
	// For compabilities
	public void startService() {
		super.startService();
	}

	// Use for specified text path file
	@Override
	public void setLanguage(String l) {
		TextPath = voice;
	}

	// Use for read text path file
	@Override
	public String getLanguage() {
		return TextPath;
	}

	// For compabilities
	@Override
	public List<String> getLanguages() {
		return null;
	}

	// For compabilities
	@Override
	public ArrayList<String> getVoices() {
		return new ArrayList<String>(voices);
	}

	@Override
	public boolean setVoice(String voice) {
		this.voice = voice;
		return true;
	}

	@Override
	public String getVoice() {
		return voice;
	}
	
	/**
	 * Execute command shell
	 * 
	 * @param command
	 *          - the command line to execute.
	 * @return TODO
	 */
	private String executeCommand(String command) {
	
		StringBuffer output = new StringBuffer();
		Process p;
	
		try {
			p = Runtime.getRuntime().exec(command);
			p.waitFor();
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
	
			String line = "";
			while ((line = reader.readLine())!= null) {
				output.append(line + "\n");
			}
	
		} catch (Exception e) {
			e.printStackTrace();
		}
	
		return output.toString();
	}
	
	/**
	 * Check voicetest batch if exist
	 * 
	 * @param nothing
	 * @return true or false
	 */
	private boolean batchFileIsOK() {
		
		Path p = Paths.get("voicetest.bat");
		if (Files.exists(p))
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	/**
	 * Create voicetest batch if doesn't exist
	 * 
	 * @param nothing
	 * @return nothing
	 */
	private void createBatchFile() {
		
		File f = new File ("voicetest.bat");
		 
		try
		{
			PrintWriter pw = new PrintWriter(new BufferedWriter (new FileWriter (f)));
		 
			pw.print("ptts -u text.txt");
			pw.close();
		}
		catch (IOException exception)
		{
			System.out.println("Write error : " + exception.getMessage());
		}		
	}
	
	/**
	 * Begin speaking something and return immediately
	 * 
	 * @param toSpeak
	 *          - the string of text to speak.
	 * @return TODO
	 */
	@Override
	public AudioData speak(String toSpeak) throws Exception {
		
		// Check batch file
		if (!batchFileIsOK())
		{
			createBatchFile();
		}
		
		// Text file created...
		File f = new File ("text.txt");
		 
		try
		{
			PrintWriter pw = new PrintWriter(new BufferedWriter (new FileWriter (f)));
		 
			pw.print(toSpeak);
			pw.close();
		}
		catch (IOException exception)
		{
			System.out.println("Write error : " + exception.getMessage());
		}		


		// Start speak event
		invoke("publishStartSpeaking", toSpeak);

		// Send command
		//executeCommand("voicetest.bat");
		Runtime rt = Runtime.getRuntime();
		Process pr = rt.exec("voicetest.bat");

		// End speak event
		invoke("publishEndSpeaking", toSpeak);
	
		return null;
	}

	/**
	 * Begin speaking and wait until all speech has been played back/
	 * 
	 * @param toSpeak
	 *          - the string of text to speak.
	 * @return
	 */
	@Override
	public boolean speakBlocking(String toSpeak) throws Exception {

		// Check batch file
		if (!batchFileIsOK())
		{
			createBatchFile();
		}
		
		// Text file created...
		File f = new File ("text.txt");
		 
		try
		{
			PrintWriter pw = new PrintWriter(new BufferedWriter (new FileWriter (f)));
		 
			pw.print(toSpeak);
			pw.close();
		}
		catch (IOException exception)
		{
			System.out.println("Write error : " + exception.getMessage());
		}		

		// Start speak event
		invoke("publishStartSpeaking", toSpeak);

		// Send command
		executeCommand("voicetest.bat");

		// End speak event
		invoke("publishEndSpeaking", toSpeak);
	
		return false;
	}

	@Override
	public void setVolume(float volume) {
		// TODO Auto-generated method stub		
	}

	@Override
	public float getVolume() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void interrupt() {
		// TODO Auto-generated method stub
	}

	@Override
	public String publishStartSpeaking(String utterance) {
		log.info("publishStartSpeaking {}", utterance);
		return utterance;
	}

	@Override
	public String publishEndSpeaking(String utterance) {
		log.info("publishEndSpeaking {}", utterance);
		return utterance;
	}

	@Override
	public String getLocalFileName(SpeechSynthesis provider, String toSpeak, String audioFileType)
		throws UnsupportedEncodingException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addEar(SpeechRecognizer ear) {
		// TODO Auto-generated method stub
		addListener("publishStartSpeaking", ear.getName(), "onStartSpeaking");
		addListener("publishEndSpeaking", ear.getName(), "onEndSpeaking");
	}

	@Override
	public void onRequestConfirmation(String text) {
		try {
			speakBlocking(String.format("did you say. %s", text));
		} catch (Exception e) {
			Logging.logError(e);
		}
	}

	@Override
	public void onText(String text) {
		log.info("Microsoft speech On Text Called: {}", text);
		try {
			speak(text);
		} catch (Exception e) {
			Logging.logError(e);
		}
	}

	static public ServiceType getMetaData() {
		ServiceType meta = new ServiceType(MicrosoftSpeech.class.getCanonicalName());
		
		meta.addDescription("Speech synthesis based on Microsoft speech with Jampal.");
		meta.addCategory("speech");
		meta.setSponsor("Dom14");
		// meta.addDependency("Jampal for windows", "2.1.6");
		return meta;
	}
}
