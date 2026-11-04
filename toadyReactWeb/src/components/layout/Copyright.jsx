import React from 'react';
import Typography from '@mui/material/Typography';
import Link from '@mui/material/Link';

function Copyright() {
  return (
    <Typography variant="body2" color="text.secondary" align="center" sx={{ pt: 4, pb: 2 }}>
      <b>{'Email.'}</b>{' dukkeoba@gmail.com'}<br />
      {'Copyright © '}
      <Link color="inherit" href="https://www.toady.co.kr/">
        Toady
      </Link>{' '}
      {new Date().getFullYear()}.
    </Typography>
  );
}

export default Copyright;