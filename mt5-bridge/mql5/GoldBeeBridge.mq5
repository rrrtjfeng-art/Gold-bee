//+------------------------------------------------------------------+
//| GoldBeeBridge.mq5                                                |
//| Read-only XAUUSD tick publisher for Gold Bee.                    |
//| Does not place, modify, or close orders.                         |
//+------------------------------------------------------------------+
#property strict
#property version   "1.00"
#property description "Publishes MT5 Bid/Ask ticks to a user-deployed Gold Bee relay. Read-only."

input string InpRelayBaseUrl = "https://YOUR-WORKER.workers.dev";
input string InpWriteToken = "CHANGE_ME";
input string InpSymbol = "";                 // Blank = chart symbol; set broker gold symbol if needed
input int    InpPublishIntervalMs = 1000;
input int    InpTimeoutMs = 5000;

string UrlTrim(string value)
{
   while(StringLen(value)>0 && StringSubstr(value,StringLen(value)-1,1)=="/")
      value=StringSubstr(value,0,StringLen(value)-1);
   return value;
}

int OnInit()
{
   if(StringLen(InpRelayBaseUrl)<12 || StringFind(InpRelayBaseUrl,"YOUR-WORKER")>=0)
   {
      Print("Gold Bee Bridge: set InpRelayBaseUrl to your deployed HTTPS Worker URL.");
      return INIT_PARAMETERS_INCORRECT;
   }
   if(StringLen(InpWriteToken)<16 || InpWriteToken=="CHANGE_ME")
   {
      Print("Gold Bee Bridge: set a private write token (at least 16 characters).");
      return INIT_PARAMETERS_INCORRECT;
   }
   int interval=InpPublishIntervalMs;
   if(interval<500) interval=500;
   EventSetMillisecondTimer(interval);
   Print("Gold Bee Bridge started in read-only mode. It will publish ticks only; no trading functions are used.");
   return INIT_SUCCEEDED;
}

void OnDeinit(const int reason)
{
   EventKillTimer();
}

void OnTimer()
{
   string symbol=InpSymbol;
   if(StringLen(symbol)==0) symbol=_Symbol;
   if(!SymbolSelect(symbol,true))
   {
      Print("Gold Bee Bridge: cannot select symbol ",symbol);
      return;
   }

   MqlTick tick;
   if(!SymbolInfoTick(symbol,tick) || tick.bid<=0 || tick.ask<tick.bid || tick.time_msc<=0)
      return;

   string body=StringFormat(
      "{\"symbol\":\"%s\",\"bid\":%.10f,\"ask\":%.10f,\"timestamp_ms\":%I64d,\"source\":\"MT5_EA\"}",
      symbol,tick.bid,tick.ask,tick.time_msc
   );
   string url=UrlTrim(InpRelayBaseUrl)+"/tick";
   string headers="Content-Type: application/json\r\nAuthorization: Bearer "+InpWriteToken+"\r\n";
   char data[], result[];
   StringToCharArray(body,data,0,WHOLE_ARRAY,CP_UTF8);
   if(ArraySize(data)>0) ArrayResize(data,ArraySize(data)-1);
   string responseHeaders;
   ResetLastError();
   int status=WebRequest("POST",url,headers,InpTimeoutMs,data,result,responseHeaders);
   if(status<200 || status>=300)
   {
      int err=GetLastError();
      if(status==-1)
         Print("Gold Bee Bridge WebRequest failed. Allow the relay HTTPS domain in MT5: Tools > Options > Expert Advisors > Allow WebRequest. Error=",err);
      else
         Print("Gold Bee Bridge relay HTTP status=",status," response=",CharArrayToString(result,0,-1,CP_UTF8));
   }
}
//+------------------------------------------------------------------+
