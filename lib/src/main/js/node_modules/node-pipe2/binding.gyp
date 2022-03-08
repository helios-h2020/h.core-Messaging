{
    "targets": [
        {
            "target_name": "pipe2",
            "sources": [ "pipe2.cc" ],
            "include_dirs" : [
 	 			"<!(node -e \"require('nan')\")"
			]
        }
    ],
}