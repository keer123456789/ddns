package com.keer.ddns;

import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.profile.DefaultProfile;
import com.alibaba.fastjson.JSON;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import com.aliyuncs.alidns.model.v20150109.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * @Author: 张经伦
 * @Date: 2022/9/15  14:17
 * @Description: DDNS
 * 第一步： 检查自身缓存是否存在自身动态IP(IPV4),目前做法是访问 自用华为云：http://119.12.168.93:18080/nas/ip 接口返回 IP
 * 第二步： 获取阿里云dns解析记录
 * 第三步： 对第一 第二步骤获取的IP进行比较
 * 第四步： 修改阿里云dns解析记录
 */
public class Main {
    private static String REGION_ID;
    private static String AK;
    private static String SK;

    private static String DOMAIN;
    private static IAcsClient client;

    private static String Url;

    private static boolean flag;

    static {
        File file = new File("config.json");
        if (file.exists()) {
            String content = "";
            StringBuilder builder = new StringBuilder();
            try {
                InputStreamReader streamReader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
                BufferedReader bufferedReader = new BufferedReader(streamReader);

                while ((content = bufferedReader.readLine()) != null) {
                    builder.append(content);
                }

                content = builder.toString();
                if (content.equals("") || !content.startsWith("{")) {
                    System.out.println("read config file error,check your config.json content!");
                    System.out.println("error config file content:" + content);
                }
                JSONObject jsonObject = JSON.parseObject(content);

                if (jsonObject.containsKey("ALI_REGION_ID")) {
                    REGION_ID = jsonObject.getString("ALI_REGION_ID");
                } else {
                    REGION_ID = "cn-hangzhou";
                }
                if (jsonObject.containsKey("ALI_AK")) {
                    AK = jsonObject.getString("ALI_AK");
                } else {
                    AK = "LT******************dUMH";
                }
                if (jsonObject.containsKey("ALI_SK")) {
                    SK = jsonObject.getString("ALI_SK");
                } else {
                    SK = "nPu68N*********bZQSI1H5j1a";
                }
                if (jsonObject.containsKey("DOMAIN")) {
                    DOMAIN = jsonObject.getString("DOMAIN");
                } else {
                    DOMAIN = "keer.life";
                }
                if (jsonObject.containsKey("Url")) {
                    Url = jsonObject.getString("Url");
                } else {
                    Url = "http://119.12.168.93:18080/nas/ip";
                }
                System.out.println("read config file success: " + content);
                DefaultProfile profile = DefaultProfile.getProfile(
                        REGION_ID,
                        AK,
                        SK);
                client = new DefaultAcsClient(profile);
                flag = true;
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("read config file error,check your config.json content!");
                flag = false;
            }
        } else {
            System.out.println("can not find config file: config.json");
            flag = false;
        }
    }

    public static void main(String[] args) {
        if (!flag) {
            return;
        }
        System.out.println("start ddns app.....");
        SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        while (true) {
            System.out.println("***********************************************");
            System.out.println("Date :" + sf.format(new Date()));
            DescribeDomainRecordsResponse.Record record = getRecordFromALIDNS();
            String dnsRecordIp = "";
            if (record != null) {
                dnsRecordIp = record.getValue();
            }
            String ip = getIP(Url);
            System.out.println("DNS record ip :" + dnsRecordIp);
            System.out.println("router ip :" + ip);
            if (!dnsRecordIp.equals(ip)) {
                System.out.println("now fix dns record ip........");
                if (updateDnsRecord(record, ip)) {
                    System.out.println("fix success , dns record ip:" + ip);
                } else {
                    System.out.println("fix error , check your code now ________________");
                }
            } else {
                System.out.println("DNS record ip equal router ip, do not fix");
            }
            System.out.println("***********************************************");
            try {
                Thread.sleep(10 * 60 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取自身公网IP
     *
     * @param url
     * @return
     */
    private static String getIP(String url) {
        CloseableHttpClient httpClient = HttpClients.createDefault();

        HttpGet httpGet = new HttpGet(url);
        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(httpGet);
        } catch (IOException e) {
            System.out.println("get请求执行异常：\n");
            e.printStackTrace();
        }
        try {
            String resp = EntityUtils.toString(response.getEntity());
            JSONObject jsonObject = JSON.parseObject(resp);
            if (jsonObject.containsKey("data")) {
                return jsonObject.getString("data");
            }
            return null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;

    }

    private static DescribeDomainRecordsResponse.Record getRecordFromALIDNS() {

        DescribeDomainRecordsRequest request = new DescribeDomainRecordsRequest();
        request.setDomainName(DOMAIN);

        try {
            DescribeDomainRecordsResponse response = client.getAcsResponse(request);
            System.out.println("ali sdk return:" + JSONObject.toJSONString(response));
            if (response.getDomainRecords().isEmpty()) {
                return null;
            } else {
                return response.getDomainRecords().get(0);
            }

        } catch (ServerException e) {
            e.printStackTrace();
        } catch (ClientException e) {
            System.out.println("ErrCode:" + e.getErrCode());
            System.out.println("ErrMsg:" + e.getErrMsg());
            System.out.println("RequestId:" + e.getRequestId());
        }
        return null;

    }

    private static boolean updateDnsRecord(DescribeDomainRecordsResponse.Record record, String url) {
        try {
            if (record == null) {
                AddDomainRecordRequest request = new AddDomainRecordRequest();
                request.setDomainName("keer.life");
                request.setLine("default");
                request.setRR("www");
                request.setTTL(600L);
                request.setType("A");
                request.setValue(url);
                AddDomainRecordResponse response = client.getAcsResponse(request);
                System.out.println("ali sdk return:" + JSONObject.toJSONString(response));
                if (null != response.getRecordId() && getRecordFromALIDNS().getValue().equals(url)) {
                    return true;
                }
            } else {
                UpdateDomainRecordRequest request = new UpdateDomainRecordRequest();
                request.setValue(url);
                request.setRecordId(record.getRecordId());
                request.setLine(record.getLine());
                request.setRR(record.getRR());
                request.setTTL(record.getTTL());
                request.setType(record.getType());
                UpdateDomainRecordResponse response = client.getAcsResponse(request);
                System.out.println("ali sdk return:" + JSONObject.toJSONString(response));
                if (null != response.getRecordId() && getRecordFromALIDNS().getValue().equals(url)) {
                    return true;
                }
            }
        } catch (ServerException e) {
            e.printStackTrace();
        } catch (ClientException e) {
            System.out.println("ErrCode:" + e.getErrCode());
            System.out.println("ErrMsg:" + e.getErrMsg());
            System.out.println("RequestId:" + e.getRequestId());
        }
        return false;
    }


}
