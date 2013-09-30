package gemha;

import gemha.servers.LwGenericMessageHandler;
import lw.utils.IApp;
import lw.utils.ShutdownInterceptor;

public class GemhaBatch {

	/**
	 * @param args settingsFileName
	 */
	public static void main(String args[]) {
		if (args.length < 1) {
			System.out.println("GemhaBatch.main(): Fatal Error at startup: Too few args.");
			System.exit(-1);
		}

		String settingsFileName = args[0];

		IApp app = new LwGenericMessageHandler(settingsFileName);

		ShutdownInterceptor shutdownInterceptor = new ShutdownInterceptor(app);

		// Register the thread to be called when the VM is shut down...
		Runtime.getRuntime().addShutdownHook(shutdownInterceptor);

		// Let's go...
		app.start();
		
		System.exit(0);
	}
}
