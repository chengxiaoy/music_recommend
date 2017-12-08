package org.chengy.service.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chengy.core.HttpHelper;
import org.chengy.infrastructure.music163secret.EncryptTools;
import org.chengy.infrastructure.music163secret.Music163ApiCons;
import org.chengy.infrastructure.music163secret.SongFactory;
import org.chengy.infrastructure.music163secret.UserFactory;
import org.chengy.model.Song;
import org.chengy.model.User;
import org.chengy.repository.SongRepository;
import org.chengy.repository.UserRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by nali on 2017/9/12.
 */
@Service
public class Crawler163music {
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private SongRepository songRepository;

	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	MongoTemplate mongoTemplate;

	public void getUserInfo(String startId) {
		LinkedList<String> ids = new LinkedList<>();
		ids.add(startId);
		while (ids.size() > 0) {
			try {
				String id = ids.peek();
				if (ids.size() < 1000) {
					List<String> fansIds = getFansId(id);
					List<String> followedIds = getFollowedId(id);
					ids.addAll(fansIds);
					ids.addAll(followedIds);
				}
				User exituser =
						userRepository.findByCommunityIdAndCommunity(id, Music163ApiCons.communityName);
				if (exituser != null) {
					continue;
				}

				String html = HttpHelper.get(Music163ApiCons.Music163UserHost + id);
				Document document = Jsoup.parse(html);
				//性别
				boolean ismale = document.select("#j-name-wrap > i").hasClass("u-icn-01");
				boolean isfemale = document.select("#j-name-wrap > i").hasClass("u-icn-02");
				int gender = 0;
				if (ismale) {
					gender = 1;
				} else if (isfemale) {
					gender = 2;
				}

				String name = document.select("#j-name-wrap > span.tit.f-ff2.s-fc0.f-thide").get(0).html();
				//个性签名
				Elements signatureinfo = document.select("#head-box > dd > div.inf.s-fc3.f-brk");
				String signature = "";
				if (signatureinfo.size() > 0) {
					signature = signatureinfo.get(0).html().split("：")[1];
				}
				//年龄
				Elements ageinfo = document.select("#age");
				Date age = null;
				if (ageinfo.size() > 0) {
					age = new Date(Long.parseLong(ageinfo.get(0).attr("data-age")));
				}

				//地区的代码逻辑
				Elements elements = document.select("#head-box > dd > div:nth-child(4) > span:nth-child(1)");
				String area = "";
				if (elements.size() > 0) {
					try {
						area = elements.get(0).html().split("：")[1];
					} catch (Exception e) {

						elements = document.select("#head-box > dd > div:nth-child(3) > span:nth-child(1)");
						area = elements.get(0).html().split("：")[1];
					}
				} else {
					elements = document.select("#head-box > dd > div.inf.s-fc3 > span");
					if (elements.size() > 0) {
						area = elements.get(0).html().split("：")[1];
					}
				}

				String avatar = document.select("#ava > img").attr("src");
				User user = UserFactory.buildUser(age, area, name, avatar, id, signature, gender);
				System.out.println(user);
				List<String> songIds = getUserLikeSong(id);
				user.setLoveSongId(songIds);

				userRepository.save(user);
				System.out.println("save user succeed:" + user.getCommunityId());

			} catch (Exception e) {
				System.out.println(ids.get(0) + " get info failed");
				e.printStackTrace();
			} finally {
				ids.poll();
			}
		}

	}

	public List<String> getFansId(String uid) throws Exception {

		String fansParam = Music163ApiCons.getFansParams(uid, 1, 100);
		Document document = EncryptTools.commentAPI(fansParam, Music163ApiCons.fansUrl);
		JsonNode root = objectMapper.readTree(document.text());
		List<JsonNode> jsonNodeList =
				root.findValue("followeds").findValues("userId");

		List<String> ids =
				jsonNodeList.stream().map(JsonNode::asText).collect(Collectors.toList());

		return ids;
	}

	public List<String> getFollowedId(String uid) throws Exception {
		String followedParam = Music163ApiCons.getFollowedParams(uid, 1, 30);
		Document document = EncryptTools.commentAPI(followedParam, Music163ApiCons.getFollowedUrl(uid));

		JsonNode root = objectMapper.readTree(document.text());

		List<String> ids =
				root.findValue("follow").findValues("userId").stream().map(ob -> ob.asText()).collect(Collectors.toList());
		return ids;
	}

	public List<String> getUserLikeSong(String uid) throws Exception {
		List<String> songIds = new ArrayList<>();
		try {
			String songRecordParam = Music163ApiCons.getSongRecordALLParams(uid, 1, 100);
			Document document = EncryptTools.commentAPI(songRecordParam, Music163ApiCons.songRecordUrl);
			JsonNode root = objectMapper.readTree(document.text());
			songIds =
					root.findValue("allData").findValues("song").stream()
							.map(ob -> ob.get("id").asText()).collect(Collectors.toList());

			System.out.println(songIds);
		} catch (Exception e) {
			System.out.println("get like song failed:" + uid);
		}
		return songIds;
	}

	/**
	 * 获取用户最近在听的歌曲
	 *
	 * @param uid 用户id
	 * @throws Exception
	 */
	public void getUserRecentSong(String uid) throws Exception {
		List<String> songIds = new ArrayList<>();

		String songRecordWeek = Music163ApiCons.getSongRecordofWeek(uid, 1, 10);
		Document document = EncryptTools.commentAPI(songRecordWeek, Music163ApiCons.songRecordUrl);
		JsonNode root = objectMapper.readTree(document.text());
		songIds =
				root.findValue("weekData").findValues("song").stream()
						.map(ob -> ob.get("id").asText()).collect(Collectors.toList());
		System.out.println(songIds);

	}

	public void getSongInfo(String songId) throws Exception {

		Song exitSong = songRepository.findSongByCommunityIdAndCommunity(songId, Music163ApiCons.communityName);
		if (exitSong != null) {
			return;
		}
		String html =
				HttpHelper.get(Music163ApiCons.songHostUrl + songId);
		Document document = Jsoup.parse(html);

		Elements titleEle = document.select("body > div.g-bd4.f-cb > div.g-mn4 > div > div > div.m-lycifo > div.f-cb > div.cnt > div.hd > div > em");
		String title = titleEle.get(0).html();
		Elements artsELes = document.select("body > div.g-bd4.f-cb > div.g-mn4 > div > div > div.m-lycifo > div.f-cb > div.cnt > p:nth-child(2)");
		String art = artsELes.text().split("：")[1].trim();

		Elements albumEle = document.select("body > div.g-bd4.f-cb > div.g-mn4 > div > div > div.m-lycifo > div.f-cb > div.cnt > p:nth-child(3) > a");
		String albumTitle = albumEle.get(0).html();
		String albumId = albumEle.get(0).attr("href").split("id=")[1];

		List<String> arts = new ArrayList<>();
		Arrays.asList(art.split("/")).forEach(ob -> arts.add(ob.trim()));

		String lyric = getLyric(songId);
		ObjectMapper objectMapper = new ObjectMapper();

		JsonNode root = objectMapper.readTree(lyric);
		try {
			try {
				lyric = root.findValue("lrc").findValue("lyric").asText();
			} catch (Exception e) {
				lyric = root.findValue("tlyric").findValue("lyric").asText();
			}
			String composer = "";
			String pattern = "作曲 : .*?\n";
			Pattern r = Pattern.compile(pattern);
			Matcher matcher = r.matcher(lyric);
			while (matcher.find()) {
				composer = matcher.group().split(":")[1].trim();
			}

			String lyricist = "";
			pattern = "作词 : .*?\n";
			r = Pattern.compile(pattern);
			matcher = r.matcher(lyric);
			while (matcher.find()) {
				lyricist = matcher.group().split(":")[1].trim();
			}

			Song song = SongFactory.buildSong(songId, lyric, arts, albumTitle, albumId, title, composer, lyricist);
			System.out.println(song);
			songRepository.save(song);
		} catch (Exception e) {
			Song song = SongFactory.buildSong(songId, "", arts, albumTitle, albumId, title, "", "");
			System.out.println(song);
			songRepository.save(song);
		}

	}

	public String getLyric(String songId) throws Exception {
		String params = Music163ApiCons.getLyricParams(songId);
		System.out.println(params);
		String lyricUrl = Music163ApiCons.lyricUrl;
		Document document = EncryptTools.commentAPI(params, lyricUrl);
		return document.text();
	}

	/**
	 * 获取某个用户总共听过多少歌
	 * @param uid
	 * @return
	 * @throws Exception
	 */
	public int getRecordSongNum(String uid) throws Exception {

		String html = HttpHelper.get(Music163ApiCons.Music163UserHost + uid);
		Document document = Jsoup.parse(html);
		String songNums =
				document.select("#rHeader > h4").get(0).html();
		return Integer.parseInt(songNums.substring(4,songNums.length()-1));
	}

	public static void main(String[] args) throws Exception {

		Crawler163music crawler163music = new Crawler163music();
		crawler163music.getSongInfo("28854182");
		System.out.println(crawler163music.getLyric("28854182"));
	}


}
