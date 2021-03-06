package gov.nasa.gibs.tie.handlers.amsr2

import gov.nasa.gibs.tie.handlers.amsr2.AMSR2Configurator;
import gov.nasa.horizon.handlers.framework.ApplicationConfigurator
import gov.nasa.horizon.handlers.framework.ProductType

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.junit.Test

/**
 * Created with IntelliJ IDEA.
 * User: thuang
 * Date: 9/3/13
 * Time: 11:08 AM
 * To change this template use File | Settings | File Templates.
 */
class AMSR2WalkerBatchTest extends GroovyTestCase {

   private static Log logger = LogFactory.getLog(gov.nasa.gibs.tie.handlers.amsr2.AMSR2WalkerBatchTest.class)

   @Test
   public void testWalkerBatch() {
      String[] args = ['-p', 'MYRCR143LLDY', '-s', '2013-08-30', '-e', '2013-08-31']
      ApplicationConfigurator configurator = new gov.nasa.gibs.tie.handlers.amsr2.AMSR2Configurator(args)
      assertFalse(configurator.hasError())

      Timer timer = new Timer()
      Map<String, ProductType> pts = configurator.productTypes
      assertEquals(4, pts.size())
      pts.each { name, pt ->
         logger.info("Processing product type: ${name}")
         if (pt.isReady()) {
            if (pt.isBatch()) {
               pt.work()
            }
         }
      }
      logger.info("Shutting down timer tasks")
      timer.cancel()
      pts.each { name, pt ->
         logger.info("Cleaning up product type: ${name}")
         //pt.cleanup()
      }
   }

}
