package com.zos.java.utilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.StringTokenizer;

import com.ibm.jzos.FileFactory;
import com.ibm.jzos.MvsJobSubmitter;

/**
 * @author gedgingt
 *
 */
public class SubmitJob {

	public static void sub(String[] args) throws IOException {

		if (args.length < 1) {
			throw new IllegalArgumentException("Missing main argument: filename");
		}
		String jobname = null;
		MvsJobSubmitter jobSubmitter = new MvsJobSubmitter();
		BufferedReader rdr = FileFactory.newBufferedReader(args[0]);

		try {
			String line;
			while ((line = rdr.readLine()) != null) {

				if (jobname == null) {
					StringTokenizer tok = new StringTokenizer(line);
					String jobToken = tok.nextToken();

					if (jobToken.startsWith("//")) {
						jobname = jobToken.substring(2);
					}
				}

				jobSubmitter.write(line);
			}
		} finally {
			if (rdr != null) {
				rdr.close();
			}
		}

		// Submits the job to the internal reader
		jobSubmitter.close();

	}
}
