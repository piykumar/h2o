import os, json, unittest, time, shutil, sys
sys.path.extend(['.','..','py'])

import h2o_cmd, h2o, h2o_hosts

class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        h2o_hosts.build_cloud_with_hosts(use_hdfs=False)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_A_Basic(self):
        for n in h2o.nodes:
            h2o.verboseprint("cloud check for:", n)            
            c = n.get_cloud()
            self.assertEqual(c['cloud_size'], len(h2o.nodes), 'inconsistent cloud size')

    def test_B_GenParity1(self):
        # Create a directory for the created dataset files. ok if already exists
        SYNDATASETS_DIR = h2o.make_syn_dir()
        # always match the run below!
        print "\nGenerating some large row count parity datasets in", SYNDATASETS_DIR,
        print "\nmay be a minute.........."
        for x in xrange (161,240,20):
            # more rows!
            y = 10000 * x
            # Have to split the string out to list for pipe
            shCmdString = "perl " + h2o.find_file("syn_scripts/parity.pl") + " 128 4 "+ str(y) + " quad"
            # FIX! as long as we're doing a couple, you'd think we wouldn't have to 
            # wait for the last one to be gen'ed here before we start the first below.
            h2o.spawn_cmd_and_wait('parity.pl', shCmdString.split(),timeout=30)
            # the algorithm for creating the path and filename is hardwired in parity.pl..i.e
            csvFilename = "parity_128_4_" + str(x) + "_quad.data"  
            sys.stdout.write('.')
            sys.stdout.flush()
        print "\nDatasets generated. Using."

        # always match the gen above!
        # Let's try it twice!
        for trials in xrange(1,7):
            # prime
            trees = 6

            for x in xrange (161,240,20):
                y = 10000 * x
                print "\nTrial:", trials, ", y:", y

                csvFilename = "parity_128_4_" + str(y) + "_quad.data"  
                csvPathname = SYNDATASETS_DIR + '/' + csvFilename
                # FIX! TBD do we always have to kick off the run from node 0?
                # random guess about length of time, varying with more hosts/nodes?
                timeoutSecs = 20 + 5*(len(h2o.nodes))

                # change the model name each iteration, so they stay in h2o
                modelKey = csvFilename + "_" + str(trials)
                h2o_cmd.runRF(trees=trees, modelKey=modelKey, timeoutSecs=timeoutSecs, 
                    retryDelaySecs=1, csvPathname=csvPathname)
                sys.stdout.write('.')
                sys.stdout.flush()

                # partial clean, so we can look at tree builds from this run if hang
                h2o.clean_sandbox_stdout_stderr()
                
if __name__ == '__main__':
    h2o.unit_main()
