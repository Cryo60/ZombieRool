-- Example map logic. Paste selected calls into your own map script.
local zr = ZombieroolAPI
function OnGameStart()
  zr:setMapAtmospherePreset("ashfall")
  zr:configureMapAtmosphere({skyYaw=20, saturation=0.7, vignette=0.3, dirt=0.1})
  zr:configureAmbientSource("alarm", {
    sound="zombierool:ambient_distant_alarm", enabled=true, loop=false,
    x=32, y=70, z=-24, volume=0.7, near=6, radius=140,
    occlusion=0.3, intervalMin=900, intervalMax=2200, fadeIn=20, fadeOut=30
  })
  zr:configureAmbientSource("thunder", {flash=0.55, soundSpeed=343})
end
function OnWaveStart(wave)
  if wave % 5 == 0 then zr:triggerAmbientSound("alarm") end
end
function OnAmbientSound(id, sound)
  -- One map-wide callback per server cue (no per-player duplication).
end
