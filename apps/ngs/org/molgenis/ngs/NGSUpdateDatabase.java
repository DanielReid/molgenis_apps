package org.molgenis.ngs;


import org.molgenis.Molgenis;

public class NGSUpdateDatabase
{
	public static void main(String[] args) throws Exception
	{
		new Molgenis("apps/ngs/org/molgenis/ngs/ngs.properties").updateDb(true);
	}
}