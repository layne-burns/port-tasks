package com.nucleon.porttasks;

import java.lang.reflect.Method;
import net.runelite.client.eventbus.Subscribe;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * RuneLite's event bus refuses to register a plugin whose @Subscribe method isn't named "on" + the event's
 * class name, and the plugin then switches itself straight back off (2026-09-30: onChatMessageDiagnostics).
 */
public class SubscriberNamesTest
{
	@Test
	public void everySubscriberIsNamedAfterItsEvent()
	{
		for (Method m : PortTasksPlugin.class.getDeclaredMethods())
		{
			if (m.isAnnotationPresent(Subscribe.class))
			{
				assertEquals(m.toString(), "on" + m.getParameterTypes()[0].getSimpleName(), m.getName());
			}
		}
	}
}
