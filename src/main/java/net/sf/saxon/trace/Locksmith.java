////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Locksmith
{
	public static Map<String, Long> lockCounts = new ConcurrentHashMap<>();

	public static void countLock(String lockName)
	{
		if (lockCounts.containsKey(lockName))
		{
			lockCounts.put(lockName, lockCounts.get(lockName) + 1);
		}
		else
		{
			lockCounts.put(lockName, 1L);
		}
	}

	static
	{
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				for (Map.Entry<String, Long> entry : lockCounts.entrySet())
				{
					System.out.println("LOCK COUNT: " + entry.getKey() + " : " + entry.getValue());
				}
			}
		}));
	}
}
