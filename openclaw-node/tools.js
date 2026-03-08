'use strict';
const ANDROID_TOOLS = [
  { name:'android_snapshot', description:'Get current screen state (UI tree + screenshot fallback)', parameters:{type:'object',properties:{},required:[]} },
  { name:'android_screenshot', description:'Take screenshot only, returns base64 JPEG', parameters:{type:'object',properties:{},required:[]} },
  { name:'android_tap', description:'Tap at x,y coordinates', parameters:{type:'object',properties:{x:{type:'number'},y:{type:'number'}},required:['x','y']} },
  { name:'android_tap_element', description:'Tap element by text/resource-id/content-desc', parameters:{type:'object',properties:{selector:{type:'string'}},required:['selector']} },
  { name:'android_type', description:'Type text into focused input', parameters:{type:'object',properties:{text:{type:'string'}},required:['text']} },
  { name:'android_swipe', description:'Swipe gesture from (x1,y1) to (x2,y2)', parameters:{type:'object',properties:{x1:{type:'number'},y1:{type:'number'},x2:{type:'number'},y2:{type:'number'},duration:{type:'number'}},required:['x1','y1','x2','y2']} },
  { name:'android_key', description:'Press key. Common: BACK=4,HOME=3,RECENT=187,ENTER=66,DEL=67', parameters:{type:'object',properties:{keyCode:{type:'number'},metaState:{type:'number'}},required:['keyCode']} },
  { name:'android_navigate', description:'Launch app by package name', parameters:{type:'object',properties:{pkg:{type:'string'},activity:{type:'string'}},required:['pkg']} },
  { name:'android_wait', description:'Wait for activity to become foreground', parameters:{type:'object',properties:{pkg:{type:'string'},activity:{type:'string'},timeoutMs:{type:'number'}},required:['pkg']} },
  { name:'android_shell', description:'Execute shell command with system privileges', parameters:{type:'object',properties:{command:{type:'string'}},required:['command']} },
];

async function executeAndroidTool(node, toolName, params) {
  switch(toolName) {
    case 'android_snapshot': return node.snapshot();
    case 'android_screenshot': return { data: await node.screenshot(), mimeType: 'image/jpeg' };
    case 'android_tap': await node.tap(params.x, params.y); return { success: true };
    case 'android_tap_element': await node.tapElement(params.selector); return { success: true };
    case 'android_type': await node.type(params.text); return { success: true };
    case 'android_swipe': await node.swipe(params.x1,params.y1,params.x2,params.y2,params.duration||300); return { success: true };
    case 'android_key': await node.keyEvent(params.keyCode, params.metaState||0); return { success: true };
    case 'android_navigate': await node.navigate(params.pkg, params.activity||''); return { success: true };
    case 'android_wait': await node.waitForActivity(params.pkg, params.activity||'', params.timeoutMs||15000); return { success: true };
    case 'android_shell': return { output: await node.shell(params.command) };
    default: throw new Error('Unknown tool: ' + toolName);
  }
}

module.exports = { ANDROID_TOOLS, executeAndroidTool };
