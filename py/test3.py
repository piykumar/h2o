import os, json, unittest, time, shutil, sys
import h2o, cmd

class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        h2o.build_cloud(node_count=3)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_GenParity1(self):
        global SYNDATASETS_DIR
        global SYNSCRIPTS_DIR

        SYNDATASETS_DIR = './syn_datasets'
        if os.path.exists(SYNDATASETS_DIR):
            shutil.rmtree(SYNDATASETS_DIR)
        os.mkdir(SYNDATASETS_DIR)

        SYNSCRIPTS_DIR = './syn_scripts'

        # always match the run below!
        for x in [10000]:
            # Have to split the string out to list for pipe
            shCmdString = "perl " + SYNSCRIPTS_DIR + "/parity.pl 128 4 "+ str(x) + " quad"
            h2o.spawn_cmd_and_wait('parity.pl', shCmdString.split(),4)
            # the algorithm for creating the path and filename is hardwired in parity.pl..i.e
            csvFilename = "parity_128_4_" + str(x) + "_quad.data"  

        # always match the gen above!
        trial = 1
        for x in xrange (10000,15000,1000):
            sys.stdout.write('.')
            sys.stdout.flush()

            # just use one file for now
            csvFilename = "parity_128_4_" + str(10000) + "_quad.data"  
            csvPathname = SYNDATASETS_DIR + '/' + csvFilename

            # broke out the put separately so we can iterate a test just on the RF
            put = h2o.nodes[0].put_file(csvPathname)
            parseKey = h2o.nodes[0].parse(put['keyHref'])

            ### print 'put TimeMS:', parseKey['TimeMS']
            cmd.runRFOnly(parseKey=parseKey, trees=137, depth=45, timeoutSecs=20)

            # don't change tree count yet
            ## trees += 10
            ### timeoutSecs += 2
            trial += 1

if __name__ == '__main__':
    h2o.clean_sandbox()
    unittest.main()