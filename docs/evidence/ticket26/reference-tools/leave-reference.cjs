// Public fixture wrapper; shared setup and operation-specific source remain explicit.
require('./control-reference.cjs').run('leave').then(result=>console.log(JSON.stringify(result,null,2))).catch(error=>{console.error(error);process.exitCode=1;});
