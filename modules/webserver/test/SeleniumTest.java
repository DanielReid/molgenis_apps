package test;

import org.openqa.selenium.server.RemoteControlConfiguration;
import org.openqa.selenium.server.SeleniumServer;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.thoughtworks.selenium.DefaultSelenium;
import com.thoughtworks.selenium.HttpCommandProcessor;
import com.thoughtworks.selenium.Selenium;

public class SeleniumTest
{
	SeleniumServer server;
	HttpCommandProcessor proc;
	Selenium selenium;
	Integer deployPort = 11000;
	String pageLoadTimeout = "30000";

	@BeforeClass(alwaysRun = true)
	public void setupBeforeClass(ITestContext context) throws Exception
	{
		String seleniumHost = "localhost";
		String seleniumPort = "9080";
		String seleniumBrowser = "firefox";
		String seleniumUrl = "http://www.google.com";

		RemoteControlConfiguration rcc = new RemoteControlConfiguration();
		rcc.setSingleWindow(true);
		rcc.setPort(Integer.parseInt(seleniumPort));

		try
		{
			server = new SeleniumServer(false, rcc);
			server.boot();
		}
		catch (Exception e)
		{
			throw new IllegalStateException("Can't start selenium server", e);
		}

		proc = new HttpCommandProcessor(seleniumHost, Integer.parseInt(seleniumPort), seleniumBrowser, seleniumUrl);
		selenium = new DefaultSelenium(proc);
		selenium.start();
	}

	@Test
	public void frontPage() throws InterruptedException
	{
		selenium.open("");
		selenium.waitForPageToLoad(pageLoadTimeout);
		Assert.assertEquals(selenium.getTitle(), "Google");
	}

}