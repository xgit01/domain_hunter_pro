package burp;

import com.google.common.net.InternetDomainName;
import org.xbill.DNS.Record;
import org.xbill.DNS.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DomainNameUtils {

	//域名校验和域名提取还是要区分对待
	//domain.DomainProducer.grepDomain(String)是提取域名的，正则中包含了*号
	public static boolean isValidDomain(String domain) {
		if (null == domain) {
			return false;
		}

		final String DOMAIN_NAME_PATTERN = "^((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,6}$";
		//final String DOMAIN_NAME_PATTERN = "([A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,6}";//-this.state.scroll 这种也会被认为是合法的。
		Pattern pDomainNameOnly = Pattern.compile(DOMAIN_NAME_PATTERN);
		Matcher matcher = pDomainNameOnly.matcher(domain);
		return matcher.matches();
	}



	//http://www.xbill.org/dnsjava/dnsjava-current/examples.html
	public static HashMap<String,Set<String>> dnsquery(String domain) {
		HashMap<String,Set<String>> result = new HashMap<String,Set<String>>();
		try{
			Lookup lookup = new Lookup(domain, org.xbill.DNS.Type.A);
			lookup.run();

			Set<String> IPset = new HashSet<String>();
			Set<String> CDNSet = new HashSet<String>();
			if(lookup.getResult() == Lookup.SUCCESSFUL){
				Record[] records=lookup.getAnswers();
				for (int i = 0; i < records.length; i++) {
					ARecord a = (ARecord) records[i];
					String ip = a.getAddress().getHostAddress();
					String CName = a.getAddress().getHostName();
					if (ip!=null) {
						IPset.add(ip);
					}
					if (CName!=null) {
						CDNSet.add(CName);
					}
					//					System.out.println("getAddress "+ a.getAddress().getHostAddress());
					//					System.out.println("getAddress "+ a.getAddress().getHostName());
					//					System.out.println("getName "+ a.getName());
					//					System.out.println(a);
				}
				//				result.put("IP", IPset);
				//				result.put("CDN", CDNSet);
				//System.out.println(records);
			}
			result.put("IP", IPset);
			result.put("CDN", CDNSet);
			return result;

		}catch(Exception e){
			e.printStackTrace();
			return result;
		}
	}

	public static HashMap<String,Set<String>> dnsquery(String domain,String server) {
		HashMap<String,Set<String>> result = new HashMap<String,Set<String>>();
		try{
			Lookup lookup = new Lookup(domain, org.xbill.DNS.Type.A);
			Resolver resolver = new SimpleResolver(server);
			lookup.setResolver(resolver);
			lookup.run();

			Set<String> IPset = new HashSet<String>();
			Set<String> CDNSet = new HashSet<String>();
			if(lookup.getResult() == Lookup.SUCCESSFUL){
				Record[] records=lookup.getAnswers();
				for (int i = 0; i < records.length; i++) {
					ARecord a = (ARecord) records[i];
					String ip = a.getAddress().getHostAddress();
					String CName = a.getAddress().getHostName();
					if (ip!=null) {
						IPset.add(ip);
					}
					if (CName!=null) {
						CDNSet.add(CName);
					}
				}
			}
			result.put("IP", IPset);
			result.put("CDN", CDNSet);
			return result;

		}catch(Exception e){
			e.printStackTrace();
			return result;
		}
	}

	public static Set<String> GetAuthoritativeNameServer(String domain) {
		Set<String> NameServerSet = new HashSet<String>();
		try{
			Lookup lookup = new Lookup(domain, org.xbill.DNS.Type.NS);
			lookup.run();

			if(lookup.getResult() == Lookup.SUCCESSFUL){
				Record[] records=lookup.getAnswers();
				for (int i = 0; i < records.length; i++) {
					NSRecord a = (NSRecord) records[i];
					String server = a.getTarget().toString();
					if (server!=null) {
						NameServerSet.add(server);
					}
				}
			}
			return NameServerSet;

		}catch(Exception e){
			e.printStackTrace();
			return NameServerSet;
		}
	}

	public static List<String> ZoneTransferCheck(String domain,String NameServer) {
		List<String> Result = new ArrayList<String>();
		try {
			ZoneTransferIn zone = ZoneTransferIn.newAXFR(new Name(domain), NameServer, null);
			zone.run();
			Result = zone.getAXFR();
			BurpExtender.getStdout().println("!!! "+NameServer+" is zoneTransfer vulnerable for domain "+domain+" !");
			System.out.print(Result);
		} catch (Exception e1) {
			BurpExtender.getStdout().println(String.format("[Server:%s Domain:%s] %s", NameServer,domain,e1.getMessage()));
		}
		return Result;
	}


	public static String getRootDomain(String inputDomain) {
		try {
			if (inputDomain.toLowerCase().startsWith("http://") || inputDomain.toLowerCase().startsWith("https://")) {
				inputDomain = new URL(inputDomain).getHost();
			}
			String rootDomain =InternetDomainName.from(inputDomain).topPrivateDomain().toString();
			return rootDomain;
		}catch(Exception e) {
			return null;
			//InternetDomainName.from("www.jd.local").topPrivateDomain()//Not under a public suffix: www.jd.local
		}
	}

	public static String cleanDomain(String domain) {
		if (domain == null){
			return null;
		}
		domain = domain.toLowerCase().trim();
		if (domain.startsWith("http://")|| domain.startsWith("https://")) {
			try {
				domain = new URL(domain).getHost();
			} catch (MalformedURLException e) {
				return null;
			}
		}else {
			if (domain.contains(":")) {//处理带有端口号的域名
				domain = domain.substring(0,domain.indexOf(":"));
			}
		}

		if (domain.endsWith(".")) {
			domain = domain.substring(0,domain.length()-1);
		}

		return domain;
	}

	/**
	 * 是否是TLD域名。比如 baidu.net 是baidu.com的TLD域名
	 * 注意：www.baidu.com不是baidu.com的TLD域名，但是是子域名！！！
	 * @param domain
	 * @param rootDomain
	 */
	public static boolean isTLDDomain(String domain,String rootDomain) {
		try {
			InternetDomainName suffixDomain = InternetDomainName.from(domain).publicSuffix();
			InternetDomainName suffixRootDomain = InternetDomainName.from(rootDomain).publicSuffix();
			if (suffixDomain != null && suffixRootDomain != null){
				String suffixOfDomain = suffixDomain.toString();
				String suffixOfRootDomain = suffixRootDomain.toString();
				if (suffixOfDomain.equalsIgnoreCase(suffixOfRootDomain)) {
					return false;
				}
				String tmpDomain = Commons.replaceLast(domain, suffixOfDomain, "");
				String tmpRootdomain = Commons.replaceLast(rootDomain, suffixOfRootDomain, "");
				if (tmpDomain.endsWith("."+tmpRootdomain) || tmpDomain.equalsIgnoreCase(tmpRootdomain)) {
					return true;
				}
			}
			return false;
		}catch (java.lang.IllegalArgumentException e){
			return false;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public static void main(String[] args) {
		System.out.println(isValidDomain("-baidu.com"));
		System.out.println(isValidDomain("www1.baidu.com"));
	}
}
