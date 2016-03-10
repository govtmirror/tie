package gov.nasa.gibs.tie.handlers.modis

import gov.nasa.horizon.common.api.file.FileProduct
import gov.nasa.horizon.common.httpfetch.api.FetchException
import gov.nasa.horizon.common.httpfetch.api.HttpFetcher
import gov.nasa.horizon.common.httpfetch.api.HttpFileProduct
import gov.nasa.horizon.handlers.framework.FileHandler
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.concurrent.Executors
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Implementation of the MODIS data crawler
 *
 * @author T. Huang
 * @version $Id: $
 */
class MODISDataCrawler extends TimerTask {

   private static Log log = LogFactory.getLog(MODISDataCrawler.class)

   private final HttpFetcher fetcher
   private FileHandler handler
   private List<String> fileExpressions
   private Iterator<String> urls
   private MODISProductType productType

   // timeout value in seconds
   private int timeout = 20

   /**
    * Constructor to create a single service thread in the background
    * for processing HTML results.
    */
   public MODISDataCrawler() {
      this.fetcher = new HttpFetcher()
      this.fetcher.timeout = this.timeout
   }

   public MODISDataCrawler(MODISProductType productType,
                           Iterator<String> urls,
                           List<String> fileExpressions,
                           FileHandler handler) {
      this.fetcher = new HttpFetcher()
      this.fetcher.timeout = this.timeout
      this.urls = urls
      this.fileExpressions = fileExpressions
      if (log.debugEnabled) {
         this.fileExpressions.each {
            log.trace("Expression: ${it}")
         }
      }
      this.handler = handler
      this.productType = productType
   }

   @Override
   public void run() {
      log.debug("inside run method")
      while (this.urls.hasNext()) {
         String url = this.urls.next()
         this.walk(url, this.fileExpressions, this.handler)
      }
      log.trace("exit run method")
   }

   public void setTimeout(int timeout) {
      this.fetcher.setTimeout(timeout)
      this.timeout = timeout
   }

   /**
    * The method to perform HTTP Walk.  This is a non-blocking method,
    * that is, the input walk request is immediately queue to the background
    * service thread.  This allows the calling program to perform other
    * operations while results are being processed and files are being
    * downloads.
    *
    * @param rootURL the starting top-level URL
    * @param expressions the regular expression map <regex,
    *                    file type> to filter
    *                    embedded URLs.  If this parameter is NULL,
    *                    then all URLs will be returned.
    * @param handler the HTTP file handler.  This handler will be called when
    *                    an URL that fits the specified regular expression is found.
    */
   public void walk(final String rootURL, final List<String> expressions,
                    final FileHandler handler) {

      List<Pattern> patterns = []

      log.debug("inside walk method")

      expressions.each {
         log.trace("Compile reg: ${it}")
         patterns.add(Pattern.compile(it))
         log.trace("Done Compiling ${it}")
      }

      log.trace("Create callable object")

      // create the callable object to be executed within the Thread.
      Runnable runnable = new Runnable() {

         private int _count = 0

         /**
          * This is a recursive method to process through the modap site until
          * URLs that fit the specified regular expression is found.
          *
          * @param rootURL the top-level URL to start retrieve HTML from
          * @return the number of URLs found
          */
         public synchronized int traverse(String url) {
            try {
               String content = fetcher.getTextContent(url)
               //log.trace("Fetched content [${content}]")
               if (content != null && content.length() > 0) {
                  Set<FileProduct> files = new HashSet<>()

                  for (String line : content.split("\n")) {
                     // trying to parse HTML line
                     // <a href="RRGlobal_r02c00.2013222.aqua.1km.jpg">RRGlobal_r02c00.2013222.aqua.1km.jpg</a>           11-Aug-2013 01:24  132K
                     // To extract file link and file modification time.
                     Pattern p = Pattern.compile("<a href=\"(\\w+\\.\\w{3})" +
                           "\">\\w+\\.\\w{3}</a>\\s+(\\d{2}-\\w{3}-\\d{4})\\s+(\\d{2}:\\d{2})\\s+\\d+[K|M]?")
                     Matcher m = p.matcher(line)
                     Date modified = null
                     if (m.find()) {
                        log.trace ("Matched line: ${line}")
                        String fileRef = null
                        for (Pattern rp : patterns) {
                           Matcher rm = rp.matcher(m.group(1))
                           log.trace ("m.group(1) = ${m.group(1)}")
                           if (rm.find()) {
                              fileRef = rm.group(0)
                              SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy HH:mm")
                              // Since MODIS is in East Coast,
                              // we should force the time to reflect the timezone.
                              sdf.setTimeZone(TimeZone.getTimeZone("US/Eastern"))
                              String datestring = m.group(2) + " " + m.group(3)
                              try {
                                 modified = sdf.parse(datestring)
                              } catch (ParseException ex) {
                                 log.error("Invalid date string encountered: " + datestring)
                              }
                              break
                           }
                        }
                        if (fileRef != null) {
                           Date cend = productType.endDate ? productType.endDate.clearTime() : new Date().clearTime()
                           Date cstart = productType.startDate.clearTime()
                           Date cmodified = modified.clearTime()

                           log.trace("start=${cstart}; modified=${cmodified}; end=${cend}")
                           if (cmodified >= cstart && cmodified <= cend) {
                              log.debug("Match: " + fileRef)
                              try {
                                 // checking to see if we already have a valid URL
                                 new URL(fileRef)
                              } catch (MalformedURLException e) {
                                 fileRef = url + "/" + fileRef
                              }
                              try {
                                 HttpFileProduct hfp = fetcher.createFileProduct(fileRef)
                                 if (modified != null) {
                                    hfp.setLastModifiedTime(modified.getTime())
                                 }
                                 files.add(hfp)
                                 log.trace("Found: " + fileRef)
                              } catch (FetchException e) {
                                 log.debug(e.getMessage(), e)
                              }
                              ++this._count
                           }
                        }
                     }
                  }
                  if (files.size() > 0) {
                     if (handler != null) {
                        handler.process(files.toList())
                        files.clear()
                        handler.postprocess()
                     }
                  }
               }
            } catch (FetchException e) {
               log.debug(e.getMessage(), e)
            }
            return this._count
         }

         /**
          * The is the hook method call by the Executor to start the thread.
          *
          * @return return the number of URLs found.
          * @throws Exception
          */
         public void run() {
            log.trace("Calling traverse")
            this.traverse(rootURL)
         }

      }

      runnable.run()
   }

   /**
    * Method to clear all memory resources allocated by this walker.
    */
   public void shutdown() {
      if (this.fetcher != null) {
         this.fetcher.shutdown()
      }
   }
}
