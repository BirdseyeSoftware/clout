var config = exports;

config['Browser Tests'] = {
    environment: 'browser',
    sources: [],
    tests: [ "resources/js/clout_browser_test.js"
           , "resources/js/clout_browser_advanced_test.js"]
};

 config['Server Tests'] = {
     environment: 'node',
     sources: [],
     tests: ["resources/js/clout_node_test.js"]
 };
