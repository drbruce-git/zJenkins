package com.zos.language

import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*
import com.ibm.dbb.build.*
import com.zos.groovy.utilities.*

class BMSProcessing {

	static void main(args) {
		/*
		 * TODO: need to rework like Assembler.groovy routine, removing hardcoded items
		 */
	}
	
	public void run(args) {
		
		// receive passed arguments
		def file = args[0] 
		def fileName = new File(file).getName().toString()
		println("* Building $file using ${this.class.getName()}.groovy script")
		
		GroovyObject tools = (GroovyObject) Tools.newInstance()
		def properties = BuildProperties.getInstance()
		
		def datasets 
		datasets = Eval.me(properties.BMSsrcFiles)
		tools.createDatasets(suffixList:datasets, suffixOpts:"${properties.srcOptions}")
		datasets = Eval.me(properties.BMSloadFiles)
		tools.createDatasets(suffixList:datasets, suffixOpts:"${properties.loadOptions}")

		def member = CopyToPDS.createMemberName(file)
		def logFile = new File("${properties.workDir}/${member}.log")
		
		// copy program to PDS 
		//println("Copying ${properties.workDir}/$file to $bmsPDS($member)")
		new CopyToPDS().file(new File("${properties.workDir}/$file")).dataset(properties.bmsPDS).member(member).execute()
		
		/********************************************************************************
		 *  Building the Copybook Generation step
		 ********************************************************************************/
		// Process Assembler routine
		def bmsParms = properties.getFileProperty("BMSOpts", fileName)
		if (bmsParms == null) {
			bmsParms = properties.DefaultBMScopybookGenOpts
		}
		println("** Running BMS Processing for map $member and opts = $bmsParms")
		def copybookGen = new MVSExec().file(file).pgm(properties.asmProgram).parm(bmsParms)
		// add DD statements to the copybookGen command
		copybookGen.dd(new DDStatement().name("SYSIN").dsn("${properties.bmsPDS}($member)").options("shr").report(true))
		copybookGen.dd(new DDStatement().name("SYSPUNCH").dsn("${properties.copybookPDS}($member)").options("shr").output(true))
		copybookGen.dd(new DDStatement().name("SYSPRINT").options(properties.tempCreateOptions))
		copybookGen.dd(new DDStatement().name("SYSUT1").options(properties.tempCreateOptions))
		copybookGen.dd(new DDStatement().name("SYSUT2").options(properties.tempCreateOptions))
		copybookGen.dd(new DDStatement().name("SYSUT3").options(properties.tempCreateOptions))
		copybookGen.dd(new DDStatement().name("SYSLIB").dsn(properties.SDFHMAC).options("shr"))
		copybookGen.dd(new DDStatement().dsn(properties.MACLIB).options("shr"))
		if (properties.appMaclibs != null) {
			// for user builds concatenate the team build copbook pds
			def maclibs = Eval.me(properties.appMaclibs)
			maclibs.each { maclib ->
				//println(" Adding $syslib to SYSLIB")
				copybookGen.dd(new DDStatement().dsn(maclib).options("shr"))
			}
		}
		copybookGen.dd(new DDStatement().name("TASKLIB").dsn(properties.SASMMOD1).options("shr"))
		
		// add a copy command to the copybookGen command to copy the SYSPRINT from the temporary dataset to an HFS log file
		copybookGen.copy(new CopyToHFS().ddName("SYSPRINT").file(logFile).hfsEncoding(properties.logEncoding))
		
		/********************************************************************************
		 *  Building the Assemble step
		 ********************************************************************************/
		// Process Assembler routine
		def assemblerParms = properties.getFileProperty("AssemblerOpts", fileName)
		if (assemblerParms == null) {
			assemblerParms = properties.DefaultAssemblerCompileOpts
		}
		
		// define the MVSExec command to compile the BMS map
		println("** Running Assembler for maps $member and opts = $assemblerParms")
		def assemble = new MVSExec().file(file).pgm(properties.asmProgram).parm(assemblerParms)
		
		// add DD statements to the compile command
		assemble.dd(new DDStatement().name("SYSIN").dsn("${properties.bmsPDS}($member)").options("shr"))
		assemble.dd(new DDStatement().name("SYSPUNCH").dsn("&&TEMPOBJ").options(properties.tempCreateOptions).pass(true))
		assemble.dd(new DDStatement().name("SYSPRINT").options(properties.tempCreateOptions))
		assemble.dd(new DDStatement().name("SYSUT1").options(properties.tempCreateOptions))
		assemble.dd(new DDStatement().name("SYSUT2").options(properties.tempCreateOptions))
		assemble.dd(new DDStatement().name("SYSUT3").options(properties.tempCreateOptions))
		assemble.dd(new DDStatement().name("SYSLIB").dsn(properties.SDFHMAC).options("shr"))
		assemble.dd(new DDStatement().dsn(properties.MACLIB).options("shr"))
		if (properties.appMaclibs != null) {
			// for user builds concatenate the team build copbook pds
			def maclibs = Eval.me(properties.appMaclibs)
			maclibs.each { maclib ->
				//println(" Adding $syslib to SYSLIB")
				assemble.dd(new DDStatement().dsn(maclib).options("shr"))
			}
		}
		assemble.dd(new DDStatement().name("TASKLIB").dsn(properties.SASMMOD1).options("shr"))
		
		// add a copy command to the compile command to copy the SYSPRINT from the temporary dataset to an HFS log file
		assemble.copy(new CopyToHFS().ddName("SYSPRINT").file(logFile).hfsEncoding(properties.logEncoding).append(true))
		
		/********************************************************************************
		 *  Building the LinkEdit step
		 ********************************************************************************/
		def lkedcntl = properties.getFileProperty("LKEDCNTL", fileName)
		def lkedMember
		if (lkedcntl != null) {
			lkedMember = CopyToPDS.createMemberName(lkedcntl)
			//println("with $fileName - copying ${properties.workDir}/${properties.'src.zOS.dir'}$lkedcntl to ${properties.linkPDS}($lkedMember)")
			new CopyToPDS().file(new File("${properties.workDir}/${properties.'src.zOS.dir'}$lkedcntl")).dataset(properties.linkPDS).member(lkedMember).execute()
		}
		def linkOpts = properties.getFileProperty("LinkOpts", fileName)
		if (linkOpts == null) {
			linkOpts = properties.DefaultLinkEditOpts
		}
		println("** LinkEditing map $member with LinkEdit Parms = $linkOpts")
		def linkedit = new MVSExec().file(file).pgm(properties.linkEditProgram).parm(linkOpts)
		linkedit.dd(new DDStatement().name("SYSLIN").dsn("&&TEMPOBJ").options("shr"))
		if (lkedcntl != null) {
			//println("Using linkedit datasets = ${properties.linkPDS}($lkedMember)")
			linkedit.dd(new DDStatement().dsn("${properties.linkPDS}($lkedMember)").options("shr"))
		}
		linkedit.dd(new DDStatement().name("SYSLMOD").dsn("${properties.onlinePDS}($member)").options("old").output(true).deployType("MAPLOAD"))
		linkedit.dd(new DDStatement().name("SYSPRINT").options(properties.tempCreateOptions))
		linkedit.dd(new DDStatement().name("SYSUT1").options(properties.tempCreateOptions))
		linkedit.dd(new DDStatement().name("SYSLIB").dsn(properties.objectPDS).options("shr"))
		if (properties.appSyslibs != null) {
			// for user builds concatenate the team build copbook pds
			def syslibs = Eval.me(properties.appSyslibs)
			syslibs.each { syslib ->
				linkedit.dd(new DDStatement().dsn(syslib).options("shr"))
			}
		}
		
		// add a copy command to the linkedit command to append the SYSPRINT from the temporary dataset to the HFS log file
		linkedit.copy(new CopyToHFS().ddName("SYSPRINT").file(logFile).hfsEncoding(properties.logEncoding).append(true))
		
		/********************************************************************************
		 *  Running individual steps
		 ********************************************************************************/
		def rc = 0
		try {
			def job = new MVSJob()
			job.start()
			
				copybookGen.validateInputs()
				rc = copybookGen.execute()
				copybookGen.validateInputs()
				tools.updateBuildResult(file:"$file", rc:rc, maxRC:4, log:logFile)
				if (rc <= 4) {
					rc = assemble.execute()
					tools.updateBuildResult(file:"$file", rc:rc, maxRC:4, log:logFile)
				}
				if (rc <= 4) {
					//println(" running LinkEdit ")
					rc = linkedit.execute()
					//println(" running LinkEdit completed RC = $rc ")
					tools.updateBuildResult(file:"$file", rc:rc, maxRC:4, log:logFile)
				}
			job.stop()
		} catch (Exception e) {
			e.printStackTrace()
			copybookGen.properties
			assemble.properties
			linkedit.properties
		}	
		// execute a simple MVSJob to handle passed temporary DDs between MVSExec commands
		//def rc = new MVSJob().executable(copybookGen).executable(assemble).executable(linkedit).maxRC(0).execute()
		
		// update build result
		tools.updateBuildResult(file:"$file", rc:rc, maxRC:0, log:logFile)
	}

}
