package gov.nasa.gibs.tie.handlers.amsr2

import gov.nasa.horizon.handlers.framework.ApplicationConfigurator
import gov.nasa.gibs.tie.handlers.common.JobPool
import gov.nasa.gibs.tie.handlers.amsr2.AMSR2Configurator;
import gov.nasa.horizon.handlers.framework.ProductType

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * Created with IntelliJ IDEA.
 * User: thuang
 * Date: 9/3/13
 * Time: 11:07 AM
 * To change this template use File | Settings | File Templates.
 */
class AMSR2WalkerThreadTest extends GroovyTestCase {

   private static Log logger = LogFactory.getLog(gov.nasa.gibs.tie.handlers.amsr2.AMSR2WalkerThreadTest.class)

   public void testWalkerThread() {
      String[] args = ['-s', '2013-08-28']
      ApplicationConfigurator configurator = new gov.nasa.gibs.tie.handlers.amsr2.AMSR2Configurator(args)
      assertFalse(configurator.hasError())
      Map<String, ProductType> pts = configurator.productTypes
      assertEquals(4, pts.size())

      JobPool jobPool = new JobPool(256)
      pts.each { name, pt ->
         if (pt.isReady()) {
            if (!pt.isBatch()) {
               if (jobPool.execute(name) {
                  pt.work()
               }) {
                  logger.info("Product Type ${name} started")
               } else {
                  logger.info("Product Type ${name} start FAILED")
               }
            } else {
               logger.info("Batch Processing product type: ${name}")
               pt.work()
            }
         }
      }

      try {
         Thread.sleep(60000);
      } catch (InterruptedException e) {
         e.printStackTrace();
      }

      logger.info("Shutting down workers")
      pts.each { name, pt ->
         pt.cleanup()
      }
      jobPool.shutdown()

   }
}
